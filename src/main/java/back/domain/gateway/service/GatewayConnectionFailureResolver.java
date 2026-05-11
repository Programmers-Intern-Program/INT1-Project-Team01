package back.domain.gateway.service;

import java.util.Locale;

import back.domain.gateway.entity.GatewayConnectionStatus;
import back.domain.gateway.exception.OpenClawGatewayErrorCode;
import back.domain.gateway.exception.OpenClawGatewayException;

public final class GatewayConnectionFailureResolver {

    private GatewayConnectionFailureResolver() {
    }

    public static GatewayConnectionStatus resolveStatus(OpenClawGatewayException exception) {
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.UNAUTHORIZED
                || matchesGatewayCode(exception.gatewayErrorCode(), "UNAUTHORIZED", "AUTH_FAILED", "TOKEN_INVALID")) {
            return GatewayConnectionStatus.TOKEN_INVALID;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.RPC_TIMEOUT
                || containsIgnoreCase(exception.gatewayErrorCode(), "timeout")) {
            return GatewayConnectionStatus.TIMEOUT;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.PAIRING_REQUIRED) {
            return GatewayConnectionStatus.PAIRING_REQUIRED;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.FORBIDDEN) {
            return GatewayConnectionStatus.FORBIDDEN;
        }
        if (exception.getErrorCode() == OpenClawGatewayErrorCode.GATEWAY_DISCONNECTED
                || exception.getErrorCode() == OpenClawGatewayErrorCode.CONNECTION_FAILED
                || exception.getErrorCode() == OpenClawGatewayErrorCode.SEND_FAILED) {
            return GatewayConnectionStatus.UNREACHABLE;
        }
        return GatewayConnectionStatus.FAILED;
    }

    public static OpenClawGatewayErrorCode resolveErrorCode(GatewayConnectionStatus status) {
        return switch (status) {
            case TOKEN_INVALID -> OpenClawGatewayErrorCode.UNAUTHORIZED;
            case TIMEOUT -> OpenClawGatewayErrorCode.RPC_TIMEOUT;
            case PAIRING_REQUIRED -> OpenClawGatewayErrorCode.PAIRING_REQUIRED;
            case FORBIDDEN -> OpenClawGatewayErrorCode.FORBIDDEN;
            case UNREACHABLE -> OpenClawGatewayErrorCode.CONNECTION_FAILED;
            case CONNECTED, FAILED -> OpenClawGatewayErrorCode.RPC_ERROR;
        };
    }

    public static String resolveClientMessage(OpenClawGatewayException exception) {
        return resolveClientMessage(resolveStatus(exception));
    }

    public static String resolveClientMessage(GatewayConnectionStatus status) {
        return switch (status) {
            case TOKEN_INVALID -> "Gateway token이 올바르지 않습니다. OpenClaw에서 발급된 Gateway token을 다시 확인해 주세요.";
            case UNREACHABLE -> "Gateway에 연결할 수 없습니다. ngrok 터널이 실행 중인지, Gateway URL이 현재 주소와 일치하는지 확인해 주세요.";
            case TIMEOUT -> "Gateway 응답 시간이 초과되었습니다. OpenClaw Gateway와 ngrok 터널 상태를 확인한 뒤 다시 시도해 주세요.";
            case PAIRING_REQUIRED -> "OpenClaw Gateway 연결 승인이 필요합니다. OpenClaw Control UI에서 연결을 승인한 뒤 다시 시도해 주세요.";
            case FORBIDDEN -> "Gateway 접근 권한이 없습니다. Gateway token 권한을 확인해 주세요.";
            case FAILED -> "Gateway 요청 처리에 실패했습니다. OpenClaw Gateway 로그를 확인해 주세요.";
            case CONNECTED -> "OpenClaw Gateway 연결에 성공했습니다.";
        };
    }

    private static boolean matchesGatewayCode(String gatewayErrorCode, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(gatewayErrorCode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
