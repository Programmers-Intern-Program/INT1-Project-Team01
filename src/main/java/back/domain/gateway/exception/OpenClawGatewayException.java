package back.domain.gateway.exception;

import back.domain.gateway.client.rpc.dto.OpenClawRpcError;
import back.global.exception.ErrorCode;
import back.global.exception.ServiceException;

import java.util.regex.Pattern;

public class OpenClawGatewayException extends ServiceException {

    private static final Pattern TOKEN_ASSIGNMENT =
            Pattern.compile("(?i)(token=)([^\\s]+)");
    private static final Pattern TOKEN_KEY_VALUE =
            Pattern.compile("(?i)(token\\s*[:=]\\s*)([^\\s,}]+)");
    private static final Pattern TOKEN_JSON_PROPERTY =
            Pattern.compile("(?i)(\"token\"\\s*:\\s*\")([^\"]+)(\")");
    private static final Pattern BEARER_AUTHORIZATION =
            Pattern.compile("(?i)(Authorization:\\s*Bearer\\s+)([^\\s]+)");

    private final String gatewayErrorCode;
    private final String requestId;
    private final boolean retryable;
    private final boolean pairingRequired;

    public OpenClawGatewayException(
            ErrorCode errorCode,
            String gatewayErrorCode,
            String logMessage,
            String clientMessage,
            String requestId,
            boolean retryable,
            boolean pairingRequired
    ) {
        super(errorCode, maskSensitive(logMessage), clientMessage);
        this.gatewayErrorCode = gatewayErrorCode;
        this.requestId = requestId;
        this.retryable = retryable;
        this.pairingRequired = pairingRequired;
    }

    public String gatewayErrorCode() {
        return gatewayErrorCode;
    }

    public String requestId() {
        return requestId;
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean pairingRequired() {
        return pairingRequired;
    }

    public static OpenClawGatewayException gatewayDisconnected() {
        return new OpenClawGatewayException(
                OpenClawGatewayErrorCode.GATEWAY_DISCONNECTED,
                "gateway_disconnected",
                "OpenClaw Gateway disconnected",
                OpenClawGatewayErrorCode.GATEWAY_DISCONNECTED.defaultMessage(),
                null,
                true,
                false
        );
    }

    public static OpenClawGatewayException connectionFailed(Throwable cause) {
        return new OpenClawGatewayException(
                OpenClawGatewayErrorCode.CONNECTION_FAILED,
                "gateway_connection_failed",
                "OpenClaw Gateway connection failed: " + cause.getMessage(),
                OpenClawGatewayErrorCode.CONNECTION_FAILED.defaultMessage(),
                null,
                true,
                false
        );
    }

    public static OpenClawGatewayException sendFailed(
            String method,
            String requestId,
            Throwable cause
    ) {
        return new OpenClawGatewayException(
                OpenClawGatewayErrorCode.SEND_FAILED,
                "gateway_send_failed",
                "OpenClaw Gateway send failed: method=" + method + " requestId=" + requestId
                        + " cause=" + cause.getMessage(),
                OpenClawGatewayErrorCode.SEND_FAILED.defaultMessage(),
                requestId,
                true,
                false
        );
    }

    public static OpenClawGatewayException responseParseFailed(Throwable cause) {
        return new OpenClawGatewayException(
                OpenClawGatewayErrorCode.RESPONSE_PARSE_FAILED,
                "gateway_response_parse_failed",
                "OpenClaw Gateway response parse failed: " + cause.getMessage(),
                OpenClawGatewayErrorCode.RESPONSE_PARSE_FAILED.defaultMessage(),
                null,
                false,
                false
        );
    }

    public static OpenClawGatewayException rpcTimeout(String method, String requestId) {
        return new OpenClawGatewayException(
                OpenClawGatewayErrorCode.RPC_TIMEOUT,
                "gateway_rpc_timeout",
                "OpenClaw Gateway RPC timeout: method=" + method + " requestId=" + requestId,
                OpenClawGatewayErrorCode.RPC_TIMEOUT.defaultMessage(),
                requestId,
                true,
                false
        );
    }

    public static OpenClawGatewayException fromRpcError(
            OpenClawRpcError error,
            String fallbackRequestId
    ) {
        String code = normalizeCode(error);
        String requestId = error == null || error.requestId() == null
                ? fallbackRequestId
                : error.requestId();
        OpenClawGatewayErrorCode errorCode = resolveErrorCode(code, error);
        return new OpenClawGatewayException(
                errorCode,
                code,
                "OpenClaw Gateway RPC error: code=" + code + " requestId=" + requestId
                        + " message=" + normalizeMessage(error),
                errorCode.defaultMessage(),
                requestId,
                isRetryable(code),
                isPairingRequired(code, error)
        );
    }

    private static String maskSensitive(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = TOKEN_ASSIGNMENT.matcher(value).replaceAll("$1****");
        masked = TOKEN_KEY_VALUE.matcher(masked).replaceAll("$1****");
        masked = TOKEN_JSON_PROPERTY.matcher(masked).replaceAll("$1****$3");
        return BEARER_AUTHORIZATION.matcher(masked).replaceAll("$1****");
    }

    private static String normalizeCode(OpenClawRpcError error) {
        if (error == null || error.code() == null || error.code().isBlank()) {
            return "gateway_rpc_error";
        }
        return error.code();
    }

    private static String normalizeMessage(OpenClawRpcError error) {
        if (error == null || error.message() == null || error.message().isBlank()) {
            return "RPC error";
        }
        return error.message();
    }

    private static boolean isPairingRequired(String code, OpenClawRpcError error) {
        if (matchesAny(code, "PAIRING_REQUIRED", "NOT_PAIRED", "gateway_pairing_required")) {
            return true;
        }
        if (error == null || error.details() == null) {
            return false;
        }
        Object detailCode = error.details().get("code");
        return detailCode instanceof String stringCode
                && matchesAny(stringCode, "PAIRING_REQUIRED", "NOT_PAIRED");
    }

    private static boolean isRetryable(String code) {
        return isTimeout(code)
                || matchesAny(code, "gateway_disconnected", "gateway_connection_failed");
    }

    private static boolean isTimeout(String code) {
        return code.toLowerCase().contains("timeout");
    }

    private static boolean isUnauthorized(String code) {
        return matchesAny(code, "UNAUTHORIZED", "AUTH_FAILED", "TOKEN_INVALID");
    }

    private static boolean isForbidden(String code) {
        return matchesAny(code, "FORBIDDEN");
    }

    private static OpenClawGatewayErrorCode resolveErrorCode(String code, OpenClawRpcError error) {
        if (isPairingRequired(code, error)) {
            return OpenClawGatewayErrorCode.PAIRING_REQUIRED;
        }
        if (isForbidden(code)) {
            return OpenClawGatewayErrorCode.FORBIDDEN;
        }
        if (isUnauthorized(code)) {
            return OpenClawGatewayErrorCode.UNAUTHORIZED;
        }
        if (isTimeout(code)) {
            return OpenClawGatewayErrorCode.RPC_TIMEOUT;
        }
        return OpenClawGatewayErrorCode.RPC_ERROR;
    }

    private static boolean matchesAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
