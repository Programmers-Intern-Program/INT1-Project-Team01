package back.domain.auth.controller;

import back.domain.auth.dto.response.GoogleLoginResponse;
import back.domain.auth.dto.response.RefreshAuthTokenResponse;
import back.domain.auth.service.AuthService;
import back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("구글 로그인 성공")
    void loginWithGoogle_success() throws Exception {
        // given
        GoogleLoginResponse loginResponse =
                new GoogleLoginResponse(1L, "테스트유저", "test@test.com", "USER", "access-token", "refresh-token");
        given(authService.loginWithGoogle("testIdToken")).willReturn(loginResponse);

        // when & then
        mockMvc.perform(post("/api/v1/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"testIdToken\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("구글 로그인 - idToken 누락 시 400")
    void loginWithGoogle_missingIdToken_returns400() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_success() throws Exception {
        // given
        RefreshAuthTokenResponse refreshResponse = new RefreshAuthTokenResponse("newAccess", "newRefresh");
        given(authService.refresh("testRefreshToken")).willReturn(refreshResponse);

        // when & then
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"testRefreshToken\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("newAccess"));
    }

    @Test
    @DisplayName("토큰 재발급 - refreshToken 누락 시 400")
    void refresh_missingRefreshToken_returns400() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() throws Exception {
        // given
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");
        doNothing().when(authService).logout(1L, "testRefreshToken");

        // when & then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"testRefreshToken\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃 - 인증 헤더 없을 때 401")
    void logout_noAuthHeader_returns401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"testRefreshToken\"}"))
                .andExpect(status().isUnauthorized());
    }
}
