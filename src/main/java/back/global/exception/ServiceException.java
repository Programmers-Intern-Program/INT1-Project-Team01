package back.global.exception;

import back.global.response.RsData;
import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String logMessage;
    private final String clientMessage;

    public ServiceException(ErrorCode errorCode, String logMessage, String clientMessage) {
        super(logMessage);
        this.errorCode = errorCode;
        this.logMessage = logMessage;
        this.clientMessage = clientMessage;
    }

    public RsData<Void> getRsData() {
        return new RsData<>(null, clientMessage);
    }
}
