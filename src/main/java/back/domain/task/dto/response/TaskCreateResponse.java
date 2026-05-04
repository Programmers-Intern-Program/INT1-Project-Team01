package back.domain.task.dto.response;

import back.domain.task.domain.Task;
import back.domain.task.domain.TaskPriority;
import back.domain.task.domain.TaskStatus;
import back.domain.task.domain.TaskType;

import java.time.LocalDateTime;

public record TaskCreateResponse(
        Long taskId,
        Long workspaceId,
        String title,
        TaskType taskType,
        TaskStatus status,
        TaskPriority priority,
        Long assignedAgentId,
        Long repositoryId,
        LocalDateTime createdAt
) {
    public static TaskCreateResponse from(Task task) {
        return new TaskCreateResponse(
                task.getId(),
                task.getWorkspaceId(),
                task.getTitle(),
                task.getTaskType(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignedAgentId(),
                task.getRepositoryId(),
                task.getCreatedAt()
        );
    }
}
