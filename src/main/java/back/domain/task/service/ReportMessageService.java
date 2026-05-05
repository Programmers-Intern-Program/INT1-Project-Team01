package back.domain.task.service;

import back.domain.task.entity.AgentReport;
import back.domain.task.entity.TaskArtifact;
import back.domain.task.entity.TaskStatus;
import back.domain.task.dto.response.SlackReportMessageResponse;
import back.domain.task.repository.AgentReportRepository;
import back.domain.task.repository.TaskArtifactRepository;
import back.domain.task.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportMessageService {

    private final TaskRepository taskRepository;
    private final AgentReportRepository agentReportRepository;
    private final TaskArtifactRepository taskArtifactRepository;

    public SlackReportMessageResponse createSlackReportMessage(Long taskId) {
        validateTaskExists(taskId);

        AgentReport report = findLatestReport(taskId);
        List<TaskArtifact> artifacts =
                taskArtifactRepository.findByReportId(report.getId());

        String message = buildMessage(report, artifacts);

        return new SlackReportMessageResponse(taskId, message);
    }

    private void validateTaskExists(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Task를 찾을 수 없습니다.");
        }
    }

    private AgentReport findLatestReport(Long taskId) {
        return agentReportRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Agent 결과 보고를 찾을 수 없습니다.")
                );
    }

    private String buildMessage(
            AgentReport report,
            List<TaskArtifact> artifacts
    ) {
        StringBuilder message = new StringBuilder();

        message.append(getStatusEmoji(report.getStatus()))
                .append(" ")
                .append(report.getSummary())
                .append("\n\n");

        appendIfExists(message, "상세", report.getDetail());
        appendIfExists(message, "추천 액션", report.getRecommendedAction());

        if (!artifacts.isEmpty()) {
            message.append("\n산출물\n");

            for (TaskArtifact artifact : artifacts) {
                message.append("- ")
                        .append(artifact.getName())
                        .append(": ")
                        .append(artifact.getUrl())
                        .append("\n");
            }
        }

        return message.toString().trim();
    }

    private void appendIfExists(
            StringBuilder message,
            String title,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            message.append(title)
                    .append(": ")
                    .append(value)
                    .append("\n");
        }
    }

    private String getStatusEmoji(TaskStatus status) {
        return switch (status) {
            case COMPLETED -> "✅";
            case FAILED -> "❌";
            case WAITING_USER -> "⚠️";
            case CANCELED -> "🚫";
            default -> "ℹ️";
        };
    }
}