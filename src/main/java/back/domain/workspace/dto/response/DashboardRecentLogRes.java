package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

import back.domain.task.entity.LogLevel;

public record DashboardRecentLogRes(
        Long logId,
        Long taskExecutionId,
        Long taskId,
        Long agentId,
        LogLevel level,
        String message,
        LocalDateTime createdAt) {}
