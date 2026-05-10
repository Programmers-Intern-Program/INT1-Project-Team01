package back.domain.task.dto.response;

public record SlackReportMessageResponse(
        Long taskId,
        String channelId,
        String threadTs,
        String message
) {
}