package back.global.security;

import back.global.security.TokenAuthenticationException.TokenErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private BearerTokenResolver bearerTokenResolver;
    @Mock private RestAuthenticationEntryPoint authenticationEntryPoint;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, bearerTokenResolver, authenticationEntryPoint);
    }

    @Test
    @DisplayName("인증 헤더 없을 때 필터 통과")
    void doFilterInternal_noAuthHeader_passesThrough() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("유효한 토큰으로 인증 성공")
    void doFilterInternal_validToken_setsAuthentication() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        given(bearerTokenResolver.resolve("Bearer valid-token")).willReturn("valid-token");
        given(jwtTokenProvider.getAccessTokenPayload("valid-token"))
                .willReturn(new JwtTokenProvider.AccessTokenPayload(1L, "USER"));

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 인증 실패")
    void doFilterInternal_invalidToken_callsEntryPoint() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        given(bearerTokenResolver.resolve("Bearer invalid-token")).willReturn("invalid-token");
        given(jwtTokenProvider.getAccessTokenPayload("invalid-token"))
                .willThrow(new TokenAuthenticationException(
                        TokenErrorType.INVALID, "[test] invalid token", "유효하지 않은 토큰입니다."));

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
    }

    @Test
    @DisplayName("만료된 토큰으로 인증 실패")
    void doFilterInternal_expiredToken_callsEntryPoint() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        given(bearerTokenResolver.resolve("Bearer expired-token")).willReturn("expired-token");
        given(jwtTokenProvider.getAccessTokenPayload("expired-token"))
                .willThrow(new TokenAuthenticationException(
                        TokenErrorType.EXPIRED, "[test] expired token", "만료된 토큰입니다."));

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
    }

    @Test
    @DisplayName("리프레시 토큰 엔드포인트는 필터 제외")
    void shouldNotFilter_refreshTokenEndpoint_returnsTrue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/token/refresh");
        request.setServletPath("/api/v1/auth/token/refresh");

        // when & then
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("일반 엔드포인트는 필터 적용")
    void shouldNotFilter_otherEndpoint_returnsFalse() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/other");
        request.setServletPath("/api/v1/other");

        // when & then
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("GET 리프레시 경로는 필터 적용")
    void shouldNotFilter_getRefreshEndpoint_returnsFalse() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/token/refresh");
        request.setServletPath("/api/v1/auth/token/refresh");

        // when & then
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
