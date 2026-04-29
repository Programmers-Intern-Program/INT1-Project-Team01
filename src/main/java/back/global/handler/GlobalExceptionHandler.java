package back.global.handler;

import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.response.RsData;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<RsData<Void>> handle(NoSuchElementException ex) {
        return toResponse(CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RsData<Void>> handle(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String[] pathBits = violation.getPropertyPath().toString().split("\\.", 2);
                    String field = pathBits.length > 1 ? pathBits[1] : pathBits[0];
                    String[] messageTemplateBits =
                            violation.getMessageTemplate().split("\\.");
                    String code = messageTemplateBits.length >= 2
                            ? messageTemplateBits[messageTemplateBits.length - 2]
                            : "Validation";
                    String violationMessage = violation.getMessage();

                    return "%s-%s-%s".formatted(field, code, violationMessage);
                })
                .sorted(Comparator.comparing(String::toString))
                .collect(Collectors.joining("\n"));

        return toResponse(CommonErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handle(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .filter(error -> error instanceof FieldError)
                .map(error -> (FieldError) error)
                .map(error -> error.getDefaultMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .sorted(Comparator.comparing(String::toString))
                .collect(Collectors.joining(" "));

        return toResponse(CommonErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RsData<Void>> handle(HttpMessageNotReadableException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST, "요청 본문이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<RsData<Void>> handle(MissingRequestHeaderException ex) {
        return toResponse(
                CommonErrorCode.BAD_REQUEST,
                "%s-%s-%s".formatted(ex.getHeaderName(), "NotBlank", ex.getLocalizedMessage()));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RsData<Void>> handle(ServiceException ex) {
        log.warn(
                "[ServiceException] resultCode={}, status={}, message={}",
                ex.getErrorCode().resultCode(),
                ex.getErrorCode().statusCode(),
                ex.getLogMessage());
        return ResponseEntity.status(ex.getErrorCode().status()).body(ex.getRsData());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RsData<Void>> handle(IllegalArgumentException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RsData<Void>> handle(IllegalStateException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST_STATE, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RsData<Void>> handle(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return toResponse(errorResponse);
        }

        log.error("[GlobalExceptionHandler#handle] unexpected error", ex);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<RsData<Void>> toResponse(CommonErrorCode errorCode) {
        return toResponse(errorCode, errorCode.defaultMessage());
    }

    private ResponseEntity<RsData<Void>> toResponse(CommonErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.status()).body(new RsData<>(null, message));
    }

    private ResponseEntity<RsData<Void>> toResponse(ErrorResponse errorResponse) {
        ProblemDetail problemDetail = errorResponse.getBody();
        String message = problemDetail == null ? null : problemDetail.getDetail();
        if (message == null || message.isBlank()) {
            HttpStatus resolvedStatus = HttpStatus.resolve(errorResponse.getStatusCode().value());
            message = resolvedStatus != null
                    ? resolvedStatus.getReasonPhrase()
                    : CommonErrorCode.INTERNAL_SERVER_ERROR.defaultMessage();
        }

        return ResponseEntity.status(errorResponse.getStatusCode()).body(new RsData<>(null, message));
    }
}
