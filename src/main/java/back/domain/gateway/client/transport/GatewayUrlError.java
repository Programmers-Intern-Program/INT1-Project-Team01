package back.domain.gateway.client.transport;

public enum GatewayUrlError {
    BLANK,
    SYNTAX_INVALID,
    SCHEME_REQUIRED,
    UNSUPPORTED_SCHEME,
    HOST_REQUIRED,
    FRAGMENT_NOT_SUPPORTED
}
