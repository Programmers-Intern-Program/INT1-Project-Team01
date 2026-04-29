package back.global.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    BAD_REQUEST("400-1", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    BAD_REQUEST_STATE("400-2", HttpStatus.BAD_REQUEST, "요청 상태가 올바르지 않습니다."),
    UNAUTHORIZED("401-1", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN("403-1", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND("404-1", HttpStatus.NOT_FOUND, "해당 데이터가 존재하지 않습니다."),
    CONFLICT("409-1", HttpStatus.CONFLICT, "요청이 충돌했습니다."),
    INTERNAL_SERVER_ERROR("500-1", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final String resultCode;
    private final HttpStatus status;
    private final String defaultMessage;

    CommonErrorCode(String resultCode, HttpStatus status, String defaultMessage) {
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
