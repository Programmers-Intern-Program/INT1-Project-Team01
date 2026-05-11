package back.domain.agent.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

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
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed dependencies are injected and retained by this service.")
public class AgentDeletionServiceImpl implements AgentDeletionService {

    private final TransactionOperations transactionOperations;
    private final WorkspaceAccessValidator workspaceAccessValidator;
    private final AgentRepository agentRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Override
    public void deleteAgent(Long workspaceId, Long memberId, Long agentId) {
        AgentDeletionTarget target = requireTransactionResult(transactionOperations.execute(
                status -> resolveDeletionTarget(workspaceId, memberId, agentId)));

        deleteOpenClawAgent(workspaceId, target.openClawAgentId());

        requireTransactionResult(transactionOperations.execute(status -> {
            disableAgent(workspaceId, target.agentId());
            return Boolean.TRUE;
        }));
    }

    private AgentDeletionTarget resolveDeletionTarget(Long workspaceId, Long memberId, Long agentId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);
        Agent agent = agentRepository
                .findByIdAndWorkspaceIdAndStatusNot(agentId, workspaceId, AgentStatus.DISABLED)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[AgentDeletionServiceImpl#deleteAgent] agent not found. agentId=" + agentId
                                + ", workspaceId="
                                + workspaceId,
                        "Agent를 찾을 수 없습니다."));
        return new AgentDeletionTarget(agent.getId(), agent.getOpenClawAgentId());
    }

    private void disableAgent(Long workspaceId, Long agentId) {
        Agent agent = agentRepository
                .findByIdAndWorkspaceIdAndStatusNot(agentId, workspaceId, AgentStatus.DISABLED)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[AgentDeletionServiceImpl#disableAgent] agent not found. agentId=" + agentId
                                + ", workspaceId="
                                + workspaceId,
                        "Agent를 찾을 수 없습니다."));
        agent.disable();
    }

    private void deleteOpenClawAgent(Long workspaceId, String openClawAgentId) {
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

    private <T> T requireTransactionResult(T result) {
        return Objects.requireNonNull(result, "transaction result must not be null");
    }

    private record AgentDeletionTarget(Long agentId, String openClawAgentId) {}
}
