package back.domain.execution.service;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@RequiredArgsConstructor
public class TaskExecutionRunnerImpl implements TaskExecutionRunner {

    private static final String DEFAULT_WORKDIR_ROOT = "/data/aioffice/workspaces";

    private final TransactionOperations transactionOperations;
    private final WorkspaceRepository workspaceRepository;
    private final AgentRepository agentRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Value("${task-execution.workdir-root:/data/aioffice/workspaces}")
    private String workdirRoot;

    @Override
    public TaskExecutionRunResult run(TaskExecutionRunCommand command) {
        Objects.requireNonNull(command);
        TaskExecution execution =
                requireTransactionResult(transactionOperations.execute(status -> createQueuedExecution(command)));
        OpenClawGatewayConnectionContext context;
        try {
            context = workspaceGatewayBindingService.getConnectionContext(command.workspaceId());
        } catch (ServiceException exception) {
            execution.markFailed(exception.getClientMessage());
            return saveResult(execution, null);
        }
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();
        try {
            client.connect(context);
            markRunning(execution);
            OpenClawChatResult chatResult = client.sendChat(new OpenClawChatCommand(
                    execution.getOpenClawAgentId(),
                    execution.getOpenClawSessionKey(),
                    buildAgentMessage(command, execution),
                    UUID.randomUUID().toString()));
            execution.markSucceeded();
            return saveResult(execution, chatResult);
        } catch (OpenClawGatewayException exception) {
            execution.markFailed(exception.getClientMessage());
            return saveResult(execution, null);
        } finally {
            client.close();
        }
    }

    private TaskExecution createQueuedExecution(TaskExecutionRunCommand command) {
        workspaceRepository.findById(command.workspaceId()).orElseThrow(() -> new ServiceException(
                CommonErrorCode.NOT_FOUND,
                "[TaskExecutionRunnerImpl#createQueuedExecution] workspace not found",
                "워크스페이스가 존재하지 않습니다."));
        Agent agent = agentRepository
                .findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        command.workspaceId(), AgentStatus.READY)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[TaskExecutionRunnerImpl#createQueuedExecution] ready agent not found",
                        "실행 가능한 READY Agent가 없습니다."));

        TaskExecution execution = taskExecutionRepository.save(TaskExecution.queued(
                command.workspaceId(),
                command.taskId(),
                agent.getId(),
                agent.getOpenClawAgentId(),
                command.repositoryId(),
                resolveBranchName(command)));
        execution.assignRuntimeContext(resolveWorkdirPath(execution), resolveSessionKey(execution));
        return taskExecutionRepository.save(execution);
    }

    private void markRunning(TaskExecution execution) {
        requireTransactionResult(transactionOperations.execute(status -> {
            execution.markRunning();
            return taskExecutionRepository.save(execution);
        }));
    }

    private TaskExecutionRunResult saveResult(TaskExecution execution, OpenClawChatResult chatResult) {
        return requireTransactionResult(transactionOperations.execute(status -> {
            TaskExecution savedExecution = taskExecutionRepository.save(execution);
            return TaskExecutionRunResult.from(savedExecution, chatResult);
        }));
    }

    private String buildAgentMessage(TaskExecutionRunCommand command, TaskExecution execution) {
        String repositoryId = command.repositoryId() == null ? "none" : command.repositoryId().toString();
        return String.join(
                System.lineSeparator(),
                "TaskExecution Context",
                "- workspaceId: " + command.workspaceId(),
                "- taskId: " + command.taskId(),
                "- taskExecutionId: " + execution.getId(),
                "- repositoryId: " + repositoryId,
                "- workdirPath: " + execution.getWorkdirPath(),
                "- branchName: " + execution.getBranchName(),
                "- createPr: " + command.createPr(),
                "",
                "User Request",
                command.prompt(),
                "",
                "Final report must include status, summary, detail, recommendedAction, and artifacts when available.",
                "Do not expose GitHub PAT, Slack token, Gateway token, or any credential value.");
    }

    private String resolveBranchName(TaskExecutionRunCommand command) {
        return "ai/workspace-" + command.workspaceId() + "/task-" + command.taskId();
    }

    private String resolveWorkdirPath(TaskExecution execution) {
        return normalizedWorkdirRoot()
                + "/" + execution.getWorkspaceId()
                + "/executions/" + execution.getId()
                + "/repo";
    }

    private String resolveSessionKey(TaskExecution execution) {
        return "workspace-" + execution.getWorkspaceId() + "-execution-" + execution.getId();
    }

    private String normalizedWorkdirRoot() {
        if (workdirRoot == null || workdirRoot.isBlank()) {
            return DEFAULT_WORKDIR_ROOT;
        }
        String normalized = workdirRoot.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private <T> T requireTransactionResult(T result) {
        return Objects.requireNonNull(result, "transaction result must not be null");
    }
}
