package back.domain.gateway.client.transport;

import java.net.URI;
import java.util.Locale;

public class GatewayUrlNormalizer {

    public URI toWebSocketUri(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new GatewayUrlNormalizationException(GatewayUrlError.BLANK, "gatewayUrl must not be blank");
        }

        URI uri = createUri(gatewayUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new GatewayUrlNormalizationException(
                    GatewayUrlError.SCHEME_REQUIRED,
                    "gatewayUrl scheme is required");
        }
        validateFragment(uri);

        URI webSocketUri = switch (scheme.toLowerCase(Locale.ROOT)) {
            case "ws", "wss" -> uri;
            case "http" -> replaceScheme(uri, "ws");
            case "https" -> replaceScheme(uri, "wss");
            default -> throw new GatewayUrlNormalizationException(
                    GatewayUrlError.UNSUPPORTED_SCHEME,
                    "unsupported gatewayUrl scheme: " + scheme);
        };
        validateWebSocketUri(webSocketUri);
        return webSocketUri;
    }

    private URI createUri(String gatewayUrl) {
        try {
            return URI.create(gatewayUrl);
        } catch (IllegalArgumentException exception) {
            throw new GatewayUrlNormalizationException(
                    GatewayUrlError.SYNTAX_INVALID,
                    "gatewayUrl syntax is invalid",
                    exception);
        }
    }

    private void validateWebSocketUri(URI uri) {
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new GatewayUrlNormalizationException(
                    GatewayUrlError.HOST_REQUIRED,
                    "gatewayUrl host is required");
        }
        validateFragment(uri);
    }

    private void validateFragment(URI uri) {
        if (uri.getRawFragment() != null) {
            throw new GatewayUrlNormalizationException(
                    GatewayUrlError.FRAGMENT_NOT_SUPPORTED,
                    "gatewayUrl fragment is not supported");
        }
    }

    private URI replaceScheme(URI uri, String scheme) {
        return URI.create(scheme + ":" + uri.getRawSchemeSpecificPart());
    }
}
