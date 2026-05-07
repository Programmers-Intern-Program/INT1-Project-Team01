package back.domain.gateway.client;

public record OpenClawChatResult(String sessionKey, String finalText) {

    public OpenClawChatResult {
        sessionKey = requireNotBlank(sessionKey, "sessionKey");
        finalText = finalText == null ? "" : finalText;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
