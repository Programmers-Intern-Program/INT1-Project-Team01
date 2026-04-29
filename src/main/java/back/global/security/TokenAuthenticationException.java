package back.global.security;

import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

public class TokenAuthenticationException extends ServiceException {
    private final TokenErrorType tokenErrorType;

    public TokenAuthenticationException(TokenErrorType tokenErrorType, String logMessage, String clientMessage) {
        super(CommonErrorCode.UNAUTHORIZED, logMessage, clientMessage);
        this.tokenErrorType = tokenErrorType;
    }

    public TokenErrorType tokenErrorType() {
        return tokenErrorType;
    }

    public enum TokenErrorType {
        EXPIRED,
        INVALID
    }
}
