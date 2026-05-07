package back.domain.execution.dto.response;

import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.gateway.client.OpenClawChatResult;

public record TaskExecutionRunResult(
        Long taskExecutionId,
        Long taskId,
        Long workspaceId,
        Long agentId,
        TaskExecutionStatus status,
        String workdirPath,
        String openClawSessionKey,
        String finalText,
        String failureReason) {

    public static TaskExecutionRunResult from(TaskExecution execution, OpenClawChatResult chatResult) {
        return new TaskExecutionRunResult(
                execution.getId(),
                execution.getTaskId(),
                execution.getWorkspaceId(),
                execution.getAgentId(),
                execution.getStatus(),
                execution.getWorkdirPath(),
                execution.getOpenClawSessionKey(),
                chatResult == null ? null : chatResult.finalText(),
                execution.getFailureReason());
    }
}
