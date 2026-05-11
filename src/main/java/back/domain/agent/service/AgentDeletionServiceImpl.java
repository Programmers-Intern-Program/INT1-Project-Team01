package back.domain.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed dependencies are injected and retained by this service.")
public class AgentDeletionServiceImpl implements AgentDeletionService {

    private final WorkspaceAccessValidator workspaceAccessValidator;
    private final AgentRepository agentRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Override
    public void deleteAgent(Long workspaceId, Long memberId, Long agentId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);
        Agent agent = agentRepository
                .findByIdAndWorkspaceIdAndStatusNot(agentId, workspaceId, AgentStatus.DISABLED)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[AgentDeletionServiceImpl#deleteAgent] agent not found. agentId=" + agentId
                                + ", workspaceId="
                                + workspaceId,
                        "Agent를 찾을 수 없습니다."));

        deleteOpenClawAgent(workspaceId, agent);
        agent.disable();
    }

    private void deleteOpenClawAgent(Long workspaceId, Agent agent) {
        String openClawAgentId = agent.getOpenClawAgentId();
        if (openClawAgentId == null || openClawAgentId.isBlank()) {
            return;
        }

        OpenClawGatewayConnectionContext context = workspaceGatewayBindingService.getConnectionContext(workspaceId);
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();
        try {
            client.connect(context);
            client.deleteAgent(openClawAgentId, true);
        } finally {
            client.close();
        }
    }
}
