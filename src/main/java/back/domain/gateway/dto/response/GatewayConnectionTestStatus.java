package back.domain.gateway.dto.response;

public enum GatewayConnectionTestStatus {
    CONNECTED,
    UNREACHABLE,
    TOKEN_INVALID,
    TIMEOUT,
    PAIRING_REQUIRED,
    FORBIDDEN,
    FAILED
}
