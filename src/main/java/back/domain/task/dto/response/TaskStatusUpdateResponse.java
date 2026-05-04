package back.domain.task.dto.response;

import back.domain.task.domain.TaskStatus;

import java.time.LocalDateTime;

public record TaskStatusUpdateResponse(
        Long taskId,
        TaskStatus previousStatus,
        TaskStatus currentStatus,
        LocalDateTime updatedAt
) {
}
