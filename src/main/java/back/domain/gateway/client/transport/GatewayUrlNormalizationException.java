package back.domain.gateway.client.transport;

public class GatewayUrlNormalizationException extends IllegalArgumentException {

    private final GatewayUrlError error;

    public GatewayUrlNormalizationException(GatewayUrlError error, String message) {
        super(message);
        this.error = error;
    }

    public GatewayUrlNormalizationException(GatewayUrlError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public GatewayUrlError getError() {
        return error;
    }
}
