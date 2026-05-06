package back.domain.execution.service;

import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import java.util.List;
import java.util.Objects;

public record AgentExecutionResult(AgentReportSaveRequest report, List<TaskArtifactSaveRequest> artifacts) {

    public AgentExecutionResult {
        report = Objects.requireNonNull(report);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
