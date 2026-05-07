package back.domain.task.dto.response;

import java.time.LocalDateTime;

import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;

public record TaskRunResponse(
        Long taskId,
        Long workspaceId,
        String title,
        TaskType taskType,
        TaskStatus taskStatus,
        TaskPriority priority,
        Long assignedAgentId,
        Long repositoryId,
        LocalDateTime createdAt,
        Long taskExecutionId,
        TaskExecutionStatus executionStatus,
        String finalText,
        String failureReason) {

    public static TaskRunResponse from(Task task, TaskExecutionRunResult executionResult) {
        return new TaskRunResponse(
                task.getId(),
                task.getWorkspaceId(),
                task.getTitle(),
                task.getTaskType(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignedAgentId(),
                task.getRepositoryId(),
                task.getCreatedAt(),
                executionResult.taskExecutionId(),
                executionResult.status(),
                executionResult.finalText(),
                executionResult.failureReason());
    }
}
