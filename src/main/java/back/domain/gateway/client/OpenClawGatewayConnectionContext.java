package back.domain.gateway.client;

public record OpenClawGatewayConnectionContext(
        String gatewayUrl,
        String token
) {

    public OpenClawGatewayConnectionContext {
        gatewayUrl = requireNotBlank(gatewayUrl, "gatewayUrl");
        token = requireNotBlank(token, "token");
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "OpenClawGatewayConnectionContext[gatewayUrl=%s, token=****]"
                .formatted(gatewayUrl);
    }
}
