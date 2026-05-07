package back.domain.task.dto.response;

import back.domain.task.entity.AgentReport;
import back.domain.task.entity.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;

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
}