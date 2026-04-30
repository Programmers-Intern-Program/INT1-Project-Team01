package back.global.google;

import back.domain.auth.port.GoogleIdTokenVerifier;
import back.global.exception.ServiceException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenInfoVerifierTest {

    private MockWebServer mockWebServer;
    private GoogleTokenInfoVerifier verifier;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        verifier = new GoogleTokenInfoVerifier("test-client-id", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("유효한 토큰 검증 성공")
    void verify_validToken_success() throws Exception {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(validResponseBody("test-client-id", "accounts.google.com", "true"))
                .addHeader("Content-Type", "application/json"));

        // when
        GoogleIdTokenVerifier.GoogleUserInfo result = verifier.verify("test-id-token");

        // then
        assertThat(result.googleSub()).isEqualTo("sub123");
        assertThat(result.email()).isEqualTo("test@test.com");
        assertThat(result.name()).isEqualTo("테스트유저");
    }

    @Test
    @DisplayName("https issuer로도 검증 성공")
    void verify_httpsIssuer_success() throws Exception {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(validResponseBody("test-client-id", "https://accounts.google.com", "true"))
                .addHeader("Content-Type", "application/json"));

        // when
        GoogleIdTokenVerifier.GoogleUserInfo result = verifier.verify("test-id-token");

        // then
        assertThat(result.googleSub()).isEqualTo("sub123");
    }

    @Test
    @DisplayName("구글 클라이언트 ID 미설정 시 예외")
    void verify_clientIdNotConfigured_throwsException() {
        // given
        GoogleTokenInfoVerifier unconfigured = new GoogleTokenInfoVerifier("", "http://localhost");

        // when & then
        assertThatThrownBy(() -> unconfigured.verify("token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("API 비정상 상태코드 시 예외")
    void verify_nonOkStatus_throwsException() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        // when & then
        assertThatThrownBy(() -> verifier.verify("invalid-token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("audience 불일치 시 예외")
    void verify_audienceMismatch_throwsException() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(validResponseBody("wrong-client-id", "accounts.google.com", "true"))
                .addHeader("Content-Type", "application/json"));

        // when & then
        assertThatThrownBy(() -> verifier.verify("token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("허용되지 않은 issuer 시 예외")
    void verify_invalidIssuer_throwsException() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(validResponseBody("test-client-id", "invalid.issuer.com", "true"))
                .addHeader("Content-Type", "application/json"));

        // when & then
        assertThatThrownBy(() -> verifier.verify("token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("이메일 미인증 시 예외")
    void verify_emailNotVerified_throwsException() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(validResponseBody("test-client-id", "accounts.google.com", "false"))
                .addHeader("Content-Type", "application/json"));

        // when & then
        assertThatThrownBy(() -> verifier.verify("token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("이메일 인증 값이 문자열 true인 경우 성공")
    void verify_emailVerifiedAsString_success() throws Exception {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                            "sub": "sub123",
                            "email": "test@test.com",
                            "name": "테스트유저",
                            "aud": "test-client-id",
                            "iss": "accounts.google.com",
                            "email_verified": "true"
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        // when
        GoogleIdTokenVerifier.GoogleUserInfo result = verifier.verify("token");

        // then
        assertThat(result.email()).isEqualTo("test@test.com");
    }

    private String validResponseBody(String aud, String iss, String emailVerified) {
        return """
                {
                    "sub": "sub123",
                    "email": "test@test.com",
                    "name": "테스트유저",
                    "aud": "%s",
                    "iss": "%s",
                    "email_verified": %s
                }
                """.formatted(aud, iss, emailVerified);
    }
}
