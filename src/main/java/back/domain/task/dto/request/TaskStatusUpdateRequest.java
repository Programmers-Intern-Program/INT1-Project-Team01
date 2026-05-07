package back.domain.task.dto.request;

import back.domain.task.entity.TaskStatus;

public record TaskStatusUpdateRequest(
        TaskStatus status,
        String reason
) {
}
