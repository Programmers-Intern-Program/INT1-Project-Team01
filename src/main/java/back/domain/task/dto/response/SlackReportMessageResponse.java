package back.domain.task.dto.response;

public record SlackReportMessageResponse(
        Long taskId,
        String message
) {
}