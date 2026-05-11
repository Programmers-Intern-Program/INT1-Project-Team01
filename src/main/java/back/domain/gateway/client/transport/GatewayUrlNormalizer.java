package back.domain.gateway.client.transport;

import java.net.URI;
import java.util.Locale;

public class GatewayUrlNormalizer {

    public URI toWebSocketUri(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new IllegalArgumentException("gatewayUrl must not be blank");
        }

        URI uri = createUri(gatewayUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("gatewayUrl scheme is required");
        }
        validateFragment(uri);

        URI webSocketUri = switch (scheme.toLowerCase(Locale.ROOT)) {
            case "ws", "wss" -> uri;
            case "http" -> replaceScheme(uri, "ws");
            case "https" -> replaceScheme(uri, "wss");
            default -> throw new IllegalArgumentException("unsupported gatewayUrl scheme: " + scheme);
        };
        validateWebSocketUri(webSocketUri);
        return webSocketUri;
    }

    private URI createUri(String gatewayUrl) {
        try {
            return URI.create(gatewayUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("gatewayUrl syntax is invalid");
        }
    }

    private void validateWebSocketUri(URI uri) {
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("gatewayUrl host is required");
        }
        validateFragment(uri);
    }

    private void validateFragment(URI uri) {
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("gatewayUrl fragment is not supported");
        }
    }

    private URI replaceScheme(URI uri, String scheme) {
        return URI.create(scheme + ":" + uri.getRawSchemeSpecificPart());
    }
}
