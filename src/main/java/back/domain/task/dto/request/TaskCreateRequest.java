package back.domain.task.dto.request;

import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;

public record TaskCreateRequest(
        String title,
        String description,
        TaskType taskType,
        TaskPriority priority,
        Long assignedAgentId,
        Long repositoryId,
        SourceType sourceType,
        String sourceId,
        String originalRequest
) {
}
