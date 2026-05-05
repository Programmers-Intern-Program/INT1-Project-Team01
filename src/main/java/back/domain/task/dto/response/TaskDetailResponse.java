package back.domain.task.dto.response;

import back.domain.task.entity.*;

import java.time.LocalDateTime;

public record TaskDetailResponse(
        Long taskId,
        Long workspaceId,
        String title,
        String description,
        TaskType taskType,
        TaskStatus status,
        TaskPriority priority,
        Long assignedAgentId,
        Long repositoryId,
        SourceType sourceType,
        String sourceId,
        String originalRequest,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskDetailResponse from(Task task) {
        return new TaskDetailResponse(
                task.getId(),
                task.getWorkspaceId(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignedAgentId(),
                task.getRepositoryId(),
                task.getSourceType(),
                task.getSourceId(),
                task.getOriginalRequest(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
