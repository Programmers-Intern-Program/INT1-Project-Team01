package back.domain.task.dto.response;

import back.domain.task.domain.LogLevel;
import back.domain.task.domain.TaskExecutionLog;

import java.time.LocalDateTime;

public record TaskLogResponse(
        Long logId,
        Long executionId,
        LogLevel level,
        String message,
        LocalDateTime createdAt
) {
    public static TaskLogResponse from(TaskExecutionLog log) {
        return new TaskLogResponse(
                log.getId(),
                log.getExecutionId(),
                log.getLevel(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}