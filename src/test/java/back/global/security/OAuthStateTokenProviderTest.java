package back.global.security;

import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.OAuthStateTokenProvider.OAuthStatePayload;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthStateTokenProviderTest {

    private OAuthStateTokenProvider tokenProvider;
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes-long";
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        tokenProvider = new OAuthStateTokenProvider(TEST_SECRET);
        secretKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("유효한 workspaceId와 memberId로 OAuth 상태 토큰을 생성하고 정상적으로 파싱한다.")
    void generateAndParse_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 100L;

        // when
        String token = tokenProvider.generateOAuthState(workspaceId, memberId);
        OAuthStatePayload payload = tokenProvider.parseOAuthState(token);

        // then
        assertThat(token).isNotBlank();
        assertThat(payload.workspaceId()).isEqualTo(workspaceId);
        assertThat(payload.memberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("서명이 다른(위조된) 토큰을 파싱하려고 하면 BAD_REQUEST 예외가 발생한다.")
    void parseOAuthState_invalidSignature_throwsException() {
        // given
        String invalidSecret = "another-secret-key-that-is-different-from-original";
        SecretKey invalidKey = Keys.hmacShaKeyFor(invalidSecret.getBytes(StandardCharsets.UTF_8));

        String forgedToken = Jwts.builder()
                .claim("workspaceId", 1L)
                .claim("memberId", 100L)
                .claim("tokenType", "OAUTH_STATE")
                .signWith(invalidKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> tokenProvider.parseOAuthState(forgedToken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Invalid token signature")
                .extracting("errorCode").isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("만료된 토큰을 파싱하려고 하면 BAD_REQUEST 예외가 발생한다.")
    void parseOAuthState_expiredToken_throwsException() {
        // given
        Date past = new Date(System.currentTimeMillis() - 10000); // 10초 전 만료

        String expiredToken = Jwts.builder()
                .claim("workspaceId", 1L)
                .claim("memberId", 100L)
                .claim("tokenType", "OAUTH_STATE")
                .expiration(past)
                .signWith(secretKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> tokenProvider.parseOAuthState(expiredToken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Token expired")
                .extracting("errorCode").isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("토큰 타입이 OAUTH_STATE가 아니면 BAD_REQUEST 예외가 발생한다.")
    void parseOAuthState_mismatchedTokenType_throwsException() {
        // given
        String invalidTypeToken = Jwts.builder()
                .claim("workspaceId", 1L)
                .claim("memberId", 100L)
                .claim("tokenType", "ACCESS") // 잘못된 타입
                .signWith(secretKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> tokenProvider.parseOAuthState(invalidTypeToken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Mismatched token type")
                .extracting("errorCode").isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("토큰의 페이로드에서 workspaceId를 임의로 조작하면 서명 검증에 실패하여 차단된다.")
    void parseOAuthState_manipulatedWorkspaceId_throwsException() {
        // given
        String validToken = tokenProvider.generateOAuthState(1L, 100L);

        // 페이로드 변조 (workspaceId를 999로 변경)
        String[] parts = validToken.split("\\.");
        String header = parts[0];
        String originalPayload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        String manipulatedPayload = originalPayload.replace("\"workspaceId\":1", "\"workspaceId\":999");
        String encodedManipulatedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(manipulatedPayload.getBytes(StandardCharsets.UTF_8));

        String forgedToken = header + "." + encodedManipulatedPayload + "." + parts[2];

        // when & then
        assertThatThrownBy(() -> tokenProvider.parseOAuthState(forgedToken))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Invalid token signature")
                .extracting("errorCode").isEqualTo(CommonErrorCode.BAD_REQUEST);
    }
}