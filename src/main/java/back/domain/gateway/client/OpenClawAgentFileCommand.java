package back.domain.gateway.client;

public record OpenClawAgentFileCommand(String agentId, String name, String content) {

    public OpenClawAgentFileCommand {
        agentId = requireNotBlank(agentId, "agentId");
        name = requireNotBlank(name, "name");
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
