package back.global.google;

import back.domain.auth.port.GoogleIdTokenVerifier;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@Component
public class GoogleTokenInfoVerifier implements GoogleIdTokenVerifier {
    private static final String DEFAULT_TOKEN_INFO_BASE_URL = "https://oauth2.googleapis.com";
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> ALLOWED_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INVALID_GOOGLE_TOKEN_MESSAGE = "유효하지 않은 구글 토큰입니다.";
    private static final String GOOGLE_LOGIN_CONFIG_REQUIRED_MESSAGE = "구글 로그인 설정이 필요합니다.";
    private static final String GOOGLE_LOGIN_FAILED_MESSAGE = "구글 로그인 중 오류가 발생했습니다.";

    private final String googleClientId;
    private final String tokenInfoBaseUrl;
    private final HttpClient httpClient;

    public GoogleTokenInfoVerifier(
            @Value("${custom.oauth.google.client-id:}") String googleClientId,
            @Value("${custom.oauth.google.token-info-base-url:https://oauth2.googleapis.com}") String tokenInfoBaseUrl) {
        this.googleClientId = googleClientId;
        this.tokenInfoBaseUrl = normalizeBaseUrl(tokenInfoBaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[GoogleTokenInfoVerifier#verify] google client id is not configured",
                    GOOGLE_LOGIN_CONFIG_REQUIRED_MESSAGE);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildTokenInfoUri(idToken))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new ServiceException(
                        CommonErrorCode.UNAUTHORIZED,
                        "[GoogleTokenInfoVerifier#verify] tokeninfo status is not 200: " + response.statusCode(),
                        INVALID_GOOGLE_TOKEN_MESSAGE);
            }

            return parseAndValidate(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[GoogleTokenInfoVerifier#verify] google token verification interrupted",
                    GOOGLE_LOGIN_FAILED_MESSAGE);
        } catch (IOException e) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[GoogleTokenInfoVerifier#verify] google token verification I/O failed",
                    GOOGLE_LOGIN_FAILED_MESSAGE);
        }
    }

    private URI buildTokenInfoUri(String idToken) {
        String encodedToken = URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        String uri = "%s/tokeninfo?id_token=%s".formatted(tokenInfoBaseUrl, encodedToken);
        return URI.create(uri);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_TOKEN_INFO_BASE_URL;
        }

        String trimmedBaseUrl = baseUrl.trim();
        return trimmedBaseUrl.endsWith("/")
                ? trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1)
                : trimmedBaseUrl;
    }

    private GoogleUserInfo parseAndValidate(String responseBody) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        String googleSub = getRequiredText(root, "sub");
        String email = getRequiredText(root, "email");
        String name = getRequiredText(root, "name");
        String audience = getRequiredText(root, "aud");
        String issuer = getRequiredText(root, "iss");

        if (!googleClientId.equals(audience)) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[GoogleTokenInfoVerifier#parseAndValidate] audience mismatch",
                    INVALID_GOOGLE_TOKEN_MESSAGE);
        }

        if (!ALLOWED_ISSUERS.contains(issuer)) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[GoogleTokenInfoVerifier#parseAndValidate] issuer is not allowed",
                    INVALID_GOOGLE_TOKEN_MESSAGE);
        }

        if (!isEmailVerified(root.get("email_verified"))) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[GoogleTokenInfoVerifier#parseAndValidate] email is not verified",
                    INVALID_GOOGLE_TOKEN_MESSAGE);
        }

        return new GoogleUserInfo(googleSub, email, name);
    }

    private String getRequiredText(JsonNode root, String fieldName) {
        JsonNode fieldNode = root.get(fieldName);
        if (fieldNode == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[GoogleTokenInfoVerifier#getRequiredText] field is missing: " + fieldName,
                    INVALID_GOOGLE_TOKEN_MESSAGE);
        }

        String value = fieldNode.asText();
        if (value == null || value.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[GoogleTokenInfoVerifier#getRequiredText] field is blank: " + fieldName,
                    INVALID_GOOGLE_TOKEN_MESSAGE);
        }

        return value;
    }

    private boolean isEmailVerified(JsonNode emailVerifiedNode) {
        if (emailVerifiedNode == null) {
            return false;
        }

        if (emailVerifiedNode.isBoolean()) {
            return emailVerifiedNode.booleanValue();
        }

        return "true".equalsIgnoreCase(emailVerifiedNode.asText());
    }
}
