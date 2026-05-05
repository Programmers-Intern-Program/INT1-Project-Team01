package back.domain.task.dto.response;

import back.domain.task.entity.Task;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;

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
