package back.domain.execution.service;

import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import java.util.List;
import java.util.Objects;

public record AgentExecutionResult(
        AgentReportSaveRequest report,
        List<TaskArtifactSaveRequest> artifacts,
        List<ArtifactFileSaveCommand> files) {

    public AgentExecutionResult(AgentReportSaveRequest report, List<TaskArtifactSaveRequest> artifacts) {
        this(report, artifacts, List.of());
    }

    public AgentExecutionResult {
        report = Objects.requireNonNull(report);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public AgentExecutionStatus status() {
        return AgentExecutionStatus.from(report.status());
    }
}
