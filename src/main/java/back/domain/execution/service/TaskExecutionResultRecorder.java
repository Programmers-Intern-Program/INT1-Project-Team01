package back.domain.execution.service;

import back.domain.artifact.dto.StoredArtifactFile;
import back.domain.artifact.service.WorkspaceArtifactStorage;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.execution.entity.ExecutionTaskArtifact;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.repository.ExecutionAgentReportRepository;
import back.domain.execution.repository.ExecutionTaskArtifactRepository;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.task.entity.TaskMessage;
import back.domain.task.repository.TaskMessageRepository;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring injects service collaborators managed by the application context.")
public class TaskExecutionResultRecorder {

    private static final String FAILED_STATUS = "FAILED";
    private static final String FAILED_SUMMARY = "Agent 실행에 실패했습니다.";

    private final AgentExecutionResultParser agentExecutionResultParser;
    private final ExecutionAgentReportRepository agentReportRepository;
    private final ExecutionTaskArtifactRepository taskArtifactRepository;
    private final TaskMessageRepository taskMessageRepository;
    private final WorkspaceArtifactStorage workspaceArtifactStorage;

    public AgentExecutionResult parse(OpenClawChatResult chatResult) {
        Objects.requireNonNull(chatResult);
        return agentExecutionResultParser.parse(chatResult.finalText());
    }

    public void recordResult(TaskExecution execution, AgentExecutionResult result) {
        Objects.requireNonNull(execution);
        Objects.requireNonNull(result);
        ArtifactMergeResult artifactMergeResult = mergeStoredFileArtifacts(execution, result);
        agentReportRepository.save(ExecutionAgentReport.create(execution.getId(), result.report()));
        artifactMergeResult.artifacts().stream()
                .map(artifact -> ExecutionTaskArtifact.create(execution.getId(), artifact))
                .forEach(taskArtifactRepository::save);
        saveUserResponseMessage(
                execution,
                result.report(),
                artifactMergeResult.artifacts(),
                artifactMergeResult.warningMessage());
    }

    public void recordFailure(TaskExecution execution) {
        Objects.requireNonNull(execution);
        AgentReportSaveRequest report = new AgentReportSaveRequest(
                FAILED_STATUS,
                FAILED_SUMMARY,
                execution.getFailureReason(),
                "Gateway 설정과 OpenClaw Agent 실행 상태를 확인하세요.");
        agentReportRepository.save(ExecutionAgentReport.create(execution.getId(), report));
        saveUserResponseMessage(execution, report, List.of(), null);
    }

    private void saveUserResponseMessage(
            TaskExecution execution,
            AgentReportSaveRequest report,
            List<TaskArtifactSaveRequest> artifacts,
            String warningMessage) {
        taskMessageRepository.save(TaskMessage.assistantResponse(
                execution.getWorkspaceId(),
                execution.getTaskId(),
                execution.getId(),
                report.status(),
                buildUserResponseContent(report, artifacts, warningMessage),
                report.summary(),
                report.detail(),
                report.recommendedAction()));
    }

    private String buildUserResponseContent(
            AgentReportSaveRequest report,
            List<TaskArtifactSaveRequest> artifacts,
            String warningMessage) {
        StringBuilder builder = new StringBuilder(report.summary());
        appendSection(builder, report.detail());
        appendPrefixedSection(builder, "권장 조치", report.recommendedAction());
        appendPrefixedSection(builder, "산출물 저장 경고", warningMessage);
        appendArtifacts(builder, artifacts);
        return builder.toString();
    }

    private void appendSection(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append("\n\n").append(value);
        }
    }

    private void appendPrefixedSection(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append("\n\n").append(label).append(": ").append(value);
        }
    }

    private void appendArtifacts(StringBuilder builder, List<TaskArtifactSaveRequest> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        builder.append("\n\n산출물");
        artifacts.forEach(artifact -> builder.append("\n- ").append(formatArtifact(artifact)));
    }

    private String formatArtifact(TaskArtifactSaveRequest artifact) {
        StringBuilder builder = new StringBuilder()
                .append("[")
                .append(artifact.artifactType())
                .append("] ")
                .append(artifact.name());
        if (artifact.url() != null && !artifact.url().isBlank()) {
            builder.append(": ").append(artifact.url());
        }
        return builder.toString();
    }

    private ArtifactMergeResult mergeStoredFileArtifacts(
            TaskExecution execution, AgentExecutionResult result) {
        List<TaskArtifactSaveRequest> artifacts = new ArrayList<>(result.artifacts());
        if (result.files().isEmpty()) {
            return new ArtifactMergeResult(artifacts, null);
        }
        try {
            workspaceArtifactStorage.storeFiles(execution.getWorkspaceId(), result.files()).stream()
                    .map(this::toFileArtifact)
                    .forEach(artifacts::add);
            return new ArtifactMergeResult(artifacts, null);
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent file artifact storage failed. taskExecutionId={}, workspaceId={}",
                    execution.getId(),
                    execution.getWorkspaceId(),
                    exception);
            return new ArtifactMergeResult(artifacts, resolveStorageWarningMessage(exception));
        }
    }

    private TaskArtifactSaveRequest toFileArtifact(StoredArtifactFile file) {
        return new TaskArtifactSaveRequest("FILE_PATH", file.relativePath(), file.relativePath());
    }

    private String resolveStorageWarningMessage(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException) {
            return serviceException.getClientMessage();
        }
        return "파일 산출물 저장 중 오류가 발생했습니다.";
    }

    private record ArtifactMergeResult(List<TaskArtifactSaveRequest> artifacts, String warningMessage) {

        private ArtifactMergeResult {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }
}
