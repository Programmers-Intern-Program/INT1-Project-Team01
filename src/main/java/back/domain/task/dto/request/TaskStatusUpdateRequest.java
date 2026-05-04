package back.domain.task.dto.request;

import back.domain.task.domain.TaskStatus;

public record TaskStatusUpdateRequest(
        TaskStatus status,
        String reason
) {
}
