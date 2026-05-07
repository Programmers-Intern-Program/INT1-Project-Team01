package back.domain.task.dto.response;

import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.task.entity.AgentReport;
import back.domain.task.entity.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public record AgentReportResponse(
        Long reportId,
        Long taskId,
        TaskStatus status,
        String summary,
        String detail,
        String recommendedAction,
        List<TaskArtifactResponse> artifacts,
        LocalDateTime createdAt
) {

    public AgentReportResponse {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static AgentReportResponse of(
            AgentReport report,
            List<TaskArtifactResponse> artifacts
    ) {
        return new AgentReportResponse(
                report.getId(),
                report.getTaskId(),
                report.getStatus(),
                report.getSummary(),
                report.getDetail(),
                report.getRecommendedAction(),
                artifacts,
                report.getCreatedAt()
        );
    }

    public static AgentReportResponse of(
            ExecutionAgentReport report,
            Long taskId,
            List<TaskArtifactResponse> artifacts
    ) {
        return new AgentReportResponse(
                report.getId(),
                taskId,
                toTaskStatus(report.getStatus()),
                report.getSummary(),
                report.getDetail(),
                report.getRecommendedAction(),
                artifacts,
                report.getCreatedAt()
        );
    }

    private static TaskStatus toTaskStatus(String value) {
        if (value == null || value.isBlank()) {
            return TaskStatus.FAILED;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("SUCCEEDED".equals(normalized) || "SUCCESS".equals(normalized) || "DONE".equals(normalized)) {
            return TaskStatus.COMPLETED;
        }
        try {
            return TaskStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return TaskStatus.FAILED;
        }
    }
}
