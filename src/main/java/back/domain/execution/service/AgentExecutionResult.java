package back.domain.execution.service;

import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Record constructor defensively copies list values before storing them.")
public record AgentExecutionResult(
        AgentReportSaveRequest report,
        List<TaskArtifactSaveRequest> artifacts,
        List<ArtifactFileSaveCommand> files,
        List<String> risks,
        List<String> nextActions) {

    public AgentExecutionResult(AgentReportSaveRequest report, List<TaskArtifactSaveRequest> artifacts) {
        this(report, artifacts, List.of());
    }

    public AgentExecutionResult(
            AgentReportSaveRequest report,
            List<TaskArtifactSaveRequest> artifacts,
            List<ArtifactFileSaveCommand> files) {
        this(report, artifacts, files, List.of(), List.of());
    }

    public AgentExecutionResult {
        report = Objects.requireNonNull(report);
        artifacts = copyList(artifacts);
        files = files == null ? List.of() : List.copyOf(files);
        risks = copyOfNullable(risks);
        nextActions = copyOfNullable(nextActions);
    }

    public AgentExecutionStatus status() {
        return AgentExecutionStatus.from(report.status());
    }

    private static <T> List<T> copyList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<String> copyOfNullable(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
