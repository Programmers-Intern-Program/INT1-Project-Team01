package back.domain.gateway.client.transport;

import java.net.URI;

public class GatewayUrlNormalizer {

    public URI toWebSocketUri(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new IllegalArgumentException("gatewayUrl must not be blank");
        }

        URI uri = URI.create(gatewayUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("gatewayUrl scheme is required");
        }

        return switch (scheme.toLowerCase()) {
            case "ws", "wss" -> uri;
            case "http" -> replaceScheme(uri, "ws");
            case "https" -> replaceScheme(uri, "wss");
            default -> throw new IllegalArgumentException("unsupported gatewayUrl scheme: " + scheme);
        };
    }

    private URI replaceScheme(URI uri, String scheme) {
        return URI.create(scheme + ":" + uri.getRawSchemeSpecificPart());
    }
}
