package back.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-unit-test-must-be-long-enough!!";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3600L, 604800L);
    }

    @Test
    @DisplayName("액세스 토큰 생성 성공")
    void generateAccessToken_success() {
        // when
        String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");

        // then
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("리프레시 토큰 생성 성공")
    void generateRefreshToken_success() {
        // when
        String token = jwtTokenProvider.generateRefreshToken(1L, "test@test.com", "USER");

        // then
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("액세스 토큰 페이로드 파싱 성공")
    void getAccessTokenPayload_success() {
        // given
        String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");

        // when
        JwtTokenProvider.AccessTokenPayload payload = jwtTokenProvider.getAccessTokenPayload(token);

        // then
        assertThat(payload.memberId()).isEqualTo(1L);
        assertThat(payload.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("액세스 토큰에서 memberId 조회 성공")
    void getMemberIdFromAccessToken_success() {
        // given
        String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");

        // when & then
        assertThat(jwtTokenProvider.getMemberIdFromAccessToken(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("액세스 토큰에서 role 조회 성공")
    void getMemberRoleFromAccessToken_success() {
        // given
        String token = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");

        // when & then
        assertThat(jwtTokenProvider.getMemberRoleFromAccessToken(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("리프레시 토큰에서 memberId 조회 성공")
    void getMemberIdFromRefreshToken_success() {
        // given
        String token = jwtTokenProvider.generateRefreshToken(1L, "test@test.com", "USER");

        // when & then
        assertThat(jwtTokenProvider.getMemberIdFromRefreshToken(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료된 액세스 토큰 파싱 시 예외")
    void getAccessTokenPayload_expiredToken_throwsException() {
        // given
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1L, 604800L);
        String token = expiredProvider.generateAccessToken(1L, "test@test.com", "USER");

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getAccessTokenPayload(token))
                .isInstanceOf(TokenAuthenticationException.class);
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 파싱 시 예외")
    void getAccessTokenPayload_malformedToken_throwsException() {
        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getAccessTokenPayload("invalid.token.value"))
                .isInstanceOf(TokenAuthenticationException.class);
    }

    @Test
    @DisplayName("리프레시 토큰으로 액세스 토큰 파싱 시 예외")
    void getAccessTokenPayload_withRefreshToken_throwsException() {
        // given
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "test@test.com", "USER");

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getAccessTokenPayload(refreshToken))
                .isInstanceOf(TokenAuthenticationException.class);
    }

    @Test
    @DisplayName("액세스 토큰으로 리프레시 memberId 조회 시 예외")
    void getMemberIdFromRefreshToken_withAccessToken_throwsException() {
        // given
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getMemberIdFromRefreshToken(accessToken))
                .isInstanceOf(TokenAuthenticationException.class);
    }
}
