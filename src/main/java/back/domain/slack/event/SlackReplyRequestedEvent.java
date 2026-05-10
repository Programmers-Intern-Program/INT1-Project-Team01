package back.domain.slack.event;

public record SlackReplyRequestedEvent(
        String sourceRef,
        String message,
        String deduplicationKey) {

    public SlackReplyRequestedEvent {
        sourceRef = requireNotBlank(sourceRef, "sourceRef");
        message = requireNotBlank(message, "message");
        deduplicationKey = normalizeOptional(deduplicationKey);
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
