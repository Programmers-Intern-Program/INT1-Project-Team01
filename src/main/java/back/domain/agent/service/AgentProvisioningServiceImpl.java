package back.domain.agent.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import back.domain.agent.dto.request.AgentSkillFileReq;
import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;
import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.repository.AgentRepository;
import back.domain.agent.repository.AgentSkillFileRepository;
import back.domain.gateway.client.OpenClawAgentCreateCommand;
import back.domain.gateway.client.OpenClawAgentFileCommand;
import back.domain.gateway.client.OpenClawAgentSummary;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentProvisioningServiceImpl implements AgentProvisioningService {

    private final TransactionOperations transactionOperations;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final AgentRepository agentRepository;
    private final AgentSkillFileRepository agentSkillFileRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Override
    public OpenClawAgentCreateRes createAgent(Long workspaceId, Long memberId, OpenClawAgentCreateReq request) {
        AgentProvisioningTarget target = requireTransactionResult(transactionOperations.execute(
                status -> createProvisioningTarget(workspaceId, memberId, request)));

        OpenClawGatewayConnectionContext context;
        try {
            context = workspaceGatewayBindingService.getConnectionContext(workspaceId);
        } catch (ServiceException exception) {
            target.agent().markError(exception.getClientMessage());
            return saveProvisioningResult(target);
        }

        provisionOpenClawAgent(target, context, request.emoji());
        return saveProvisioningResult(target);
    }

    private AgentProvisioningTarget createProvisioningTarget(
            Long workspaceId, Long memberId, OpenClawAgentCreateReq request) {
        WorkspaceMember admin = requireAdmin(workspaceId, memberId);
        String agentName = requireNotBlank(request.name(), "name");
        validateDuplicateAgentName(workspaceId, agentName);

        Agent agent = agentRepository.save(Agent.create(
                admin.getWorkspace(), agentName, resolveWorkspacePath(workspaceId, request.workspacePath()), memberId));
        List<AgentSkillFile> skillFiles = saveSkillFiles(agent, request.skillFiles());
        return new AgentProvisioningTarget(workspaceId, agent, skillFiles);
    }

    private OpenClawAgentCreateRes saveProvisioningResult(AgentProvisioningTarget target) {
        return requireTransactionResult(transactionOperations.execute(status -> {
            Agent savedAgent = agentRepository.save(target.agent());
            List<AgentSkillFile> savedSkillFiles =
                    target.skillFiles().stream().map(agentSkillFileRepository::save).toList();
            return OpenClawAgentCreateRes.from(savedAgent, savedSkillFiles);
        }));
    }

    private void provisionOpenClawAgent(
            AgentProvisioningTarget target, OpenClawGatewayConnectionContext context, String emoji) {
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();
        try {
            client.connect(context);
            OpenClawAgentSummary summary = client.createAgent(
                    new OpenClawAgentCreateCommand(
                            resolveOpenClawAgentName(target), target.agent().getWorkspacePath(), emoji));
            target.agent().markOpenClawCreated(summary.agentId());
            syncSkillFiles(client, target.agent(), target.skillFiles());
        } catch (OpenClawGatewayException exception) {
            target.agent().markError(exception.getClientMessage());
        } finally {
            client.close();
        }
    }

    private void syncSkillFiles(OpenClawGatewayClient client, Agent agent, List<AgentSkillFile> skillFiles) {
        boolean failed = false;
        for (AgentSkillFile skillFile : skillFiles) {
            try {
                client.setAgentFile(new OpenClawAgentFileCommand(
                        agent.getOpenClawAgentId(), skillFile.getFileName(), skillFile.getContent()));
                skillFile.markSynced();
            } catch (OpenClawGatewayException exception) {
                skillFile.markFailed(exception.getClientMessage());
                failed = true;
            }
        }
        if (failed) {
            agent.markSyncFailed("일부 Skill 파일을 OpenClaw Agent에 동기화하지 못했습니다.");
            return;
        }
        agent.markReady();
    }

    private List<AgentSkillFile> saveSkillFiles(Agent agent, List<AgentSkillFileReq> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(request -> agentSkillFileRepository.save(
                        AgentSkillFile.create(agent, request.fileName(), request.content())))
                .toList();
    }

    private WorkspaceMember requireAdmin(Long workspaceId, Long memberId) {
        workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[AgentProvisioningServiceImpl#requireAdmin] workspace not found",
                        "워크스페이스가 존재하지 않습니다."));
        WorkspaceMember workspaceMember = workspaceMemberRepository
                .findByWorkspaceIdAndMemberId(workspaceId, memberId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.FORBIDDEN,
                        "[AgentProvisioningServiceImpl#requireAdmin] workspace membership not found",
                        "워크스페이스 접근 권한이 없습니다."));
        if (workspaceMember.getRole() != WorkspaceMemberRole.ADMIN) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[AgentProvisioningServiceImpl#requireAdmin] workspace member is not admin",
                    "워크스페이스 관리자 권한이 필요합니다.");
        }
        if (workspaceMember.getWorkspace() == null) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[AgentProvisioningServiceImpl#requireAdmin] workspace member has no workspace",
                    "워크스페이스 정보를 확인하지 못했습니다.");
        }
        return workspaceMember;
    }

    private void validateDuplicateAgentName(Long workspaceId, String agentName) {
        if (agentRepository.existsByWorkspaceIdAndName(workspaceId, agentName)) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[AgentProvisioningServiceImpl#validateDuplicateAgentName] duplicate agent name",
                    "같은 이름의 Agent가 이미 존재합니다.");
        }
    }

    private String resolveWorkspacePath(Long workspaceId, String workspacePath) {
        if (workspacePath != null && !workspacePath.isBlank()) {
            return workspacePath.trim();
        }
        return "~/.openclaw/workspace-" + workspaceId;
    }

    private String resolveOpenClawAgentName(AgentProvisioningTarget target) {
        return "workspace-" + target.workspaceId() + "-agent-" + target.agent().getId();
    }

    private String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[AgentProvisioningServiceImpl#requireNotBlank] " + fieldName + " is blank",
                    fieldName + " 값은 필수입니다.");
        }
        return value.trim();
    }

    private <T> T requireTransactionResult(T result) {
        return Objects.requireNonNull(result, "transaction result must not be null");
    }

    private record AgentProvisioningTarget(Long workspaceId, Agent agent, List<AgentSkillFile> skillFiles) {}
}
