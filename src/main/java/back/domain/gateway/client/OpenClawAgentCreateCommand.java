package back.domain.gateway.client;

public record OpenClawAgentCreateCommand(String name, String workspace, String emoji) {

    public OpenClawAgentCreateCommand {
        name = requireNotBlank(name, "name");
        workspace = normalizeOptional(workspace);
        emoji = normalizeOptional(emoji);
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
