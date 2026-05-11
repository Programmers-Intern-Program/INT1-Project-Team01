package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

public record DashboardRecentReportRes(
        Long reportId,
        Long taskExecutionId,
        Long taskId,
        Long agentId,
        String status,
        String summary,
        LocalDateTime createdAt) {}
