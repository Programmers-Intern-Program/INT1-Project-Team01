package back.domain.workspace.dto.response;

import java.util.List;

public record WorkspaceDashboardSummaryRes(
        long agentCount,
        long runningAgentCount,
        long idleAgentCount,
        long errorAgentCount,
        long taskCount,
        long runningTaskCount,
        long completedTaskCount,
        long failedTaskCount,
        List<DashboardRecentReportRes> recentReports,
        List<DashboardRecentLogRes> recentLogs) {

    public WorkspaceDashboardSummaryRes {
        recentReports = recentReports == null ? List.of() : List.copyOf(recentReports);
        recentLogs = recentLogs == null ? List.of() : List.copyOf(recentLogs);
    }
}
