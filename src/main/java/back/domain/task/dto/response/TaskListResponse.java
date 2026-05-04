package back.domain.task.dto.response;

import back.domain.task.domain.Task;
import back.domain.task.domain.TaskPriority;
import back.domain.task.domain.TaskStatus;
import back.domain.task.domain.TaskType;

import java.time.LocalDateTime;

public record TaskListResponse(
        Long taskId,
        String title,
        TaskType taskType,
        TaskStatus status,
        TaskPriority priority,
        Long assignedAgentId,
        Long repositoryId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskListResponse from(Task task) {
        return new TaskListResponse(
                task.getId(),
                task.getTitle(),
                task.getTaskType(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignedAgentId(),
                task.getRepositoryId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
