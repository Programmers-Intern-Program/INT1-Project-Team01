package back.domain.gateway.client;

public record OpenClawChatCommand(
        String openClawAgentId, String sessionKey, String message, String idempotencyKey) {

    public OpenClawChatCommand {
        openClawAgentId = requireNotBlank(openClawAgentId, "openClawAgentId");
        sessionKey = requireNotBlank(sessionKey, "sessionKey");
        message = requireNotBlank(message, "message");
        idempotencyKey = requireNotBlank(idempotencyKey, "idempotencyKey");
    }

    public String fullSessionKey() {
        if (sessionKey.startsWith("agent:")) {
            return sessionKey;
        }
        return "agent:" + openClawAgentId + ":" + sessionKey;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
