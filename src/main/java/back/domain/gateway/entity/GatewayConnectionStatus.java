package back.domain.gateway.entity;

public enum GatewayConnectionStatus {
    CONNECTED,
    UNREACHABLE,
    TOKEN_INVALID,
    TIMEOUT,
    PAIRING_REQUIRED,
    FORBIDDEN,
    FAILED
}
