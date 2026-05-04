package back.domain.task.dto.request;

import back.domain.task.domain.SourceType;
import back.domain.task.domain.TaskPriority;
import back.domain.task.domain.TaskType;

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
