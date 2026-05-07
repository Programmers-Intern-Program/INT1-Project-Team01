package back.domain.gateway.exception;

import back.global.exception.ErrorCode;

import org.springframework.http.HttpStatus;

public enum OpenClawGatewayErrorCode implements ErrorCode {
    GATEWAY_DISCONNECTED("502-GATEWAY-1", HttpStatus.BAD_GATEWAY, "OpenClaw Gateway 연결이 끊어졌습니다."),
    CONNECTION_FAILED("502-GATEWAY-2", HttpStatus.BAD_GATEWAY, "OpenClaw Gateway 연결에 실패했습니다."),
    SEND_FAILED("502-GATEWAY-3", HttpStatus.BAD_GATEWAY, "OpenClaw Gateway 요청 전송에 실패했습니다."),
    RESPONSE_PARSE_FAILED("502-GATEWAY-4", HttpStatus.BAD_GATEWAY, "OpenClaw Gateway 응답을 해석하지 못했습니다."),
    RPC_ERROR("502-GATEWAY-5", HttpStatus.BAD_GATEWAY, "OpenClaw Gateway 요청 처리에 실패했습니다."),
    RPC_TIMEOUT("504-GATEWAY-1", HttpStatus.GATEWAY_TIMEOUT, "OpenClaw Gateway 요청 시간이 초과되었습니다."),
    PAIRING_REQUIRED("409-GATEWAY-1", HttpStatus.CONFLICT, "OpenClaw Gateway 연결 승인이 필요합니다."),
    UNAUTHORIZED("401-GATEWAY-1", HttpStatus.UNAUTHORIZED, "OpenClaw Gateway 인증에 실패했습니다."),
    FORBIDDEN("403-GATEWAY-1", HttpStatus.FORBIDDEN, "OpenClaw Gateway 접근 권한이 없습니다.");

    private final String resultCode;
    private final HttpStatus status;
    private final String defaultMessage;

    OpenClawGatewayErrorCode(String resultCode, HttpStatus status, String defaultMessage) {
        this.resultCode = resultCode;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String resultCode() {
        return resultCode;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
