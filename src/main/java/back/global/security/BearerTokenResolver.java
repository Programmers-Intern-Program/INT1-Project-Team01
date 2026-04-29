package back.global.security;

import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenResolver {
    private static final String BEARER_PREFIX = "Bearer ";

    public String resolve(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[BearerTokenResolver#resolve] authorization header is missing",
                    "Authorization 헤더는 필수입니다.");
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[BearerTokenResolver#resolve] authorization header does not start with Bearer prefix",
                    "Authorization 헤더 형식이 올바르지 않습니다.");
        }

        String accessToken =
                authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        if (accessToken.isEmpty()) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[BearerTokenResolver#resolve] bearer token is blank",
                    "Access Token이 비어 있습니다.");
        }

        return accessToken;
    }
}
