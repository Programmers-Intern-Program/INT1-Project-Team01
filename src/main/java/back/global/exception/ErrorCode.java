package back.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String resultCode();

    HttpStatus status();

    String defaultMessage();

    default int statusCode() {
        return status().value();
    }
}
