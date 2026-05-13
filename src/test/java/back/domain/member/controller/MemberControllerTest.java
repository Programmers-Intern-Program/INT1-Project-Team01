package back.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import back.domain.member.dto.response.MemberAvatarColorsRes;
import back.domain.member.dto.response.MemberProfileRes;
import back.domain.member.service.MemberProfileService;
import back.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MemberControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private MemberProfileService memberProfileService;

    private MockMvc mockMvc;
    private String accessToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        accessToken = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");
    }

    @Test
    @DisplayName("내 프로필 조회 성공")
    void getMyProfile_success() throws Exception {
        // given
        MemberProfileRes response = new MemberProfileRes(
                1L,
                "홍길동",
                "mira",
                new MemberAvatarColorsRes("#f8d4b0", "#1a1a1a", "#2a3a4a"));
        given(memberProfileService.getMyProfile(anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/members/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.displayName").value("홍길동"))
                .andExpect(jsonPath("$.data.avatarColors.skin").value("#f8d4b0"));
    }

    @Test
    @DisplayName("내 프로필 조회 - 인증 없을 때 401")
    void getMyProfile_noAuth_returns401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/members/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 프로필 수정 성공")
    void updateMyProfile_success() throws Exception {
        // given
        MemberProfileRes response = new MemberProfileRes(
                1L,
                "길동",
                "mira",
                new MemberAvatarColorsRes("#111111", "#222222", "#333333"));
        given(memberProfileService.updateMyProfile(anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/members/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "길동",
                                  "avatarKind": "mira",
                                  "avatarColors": {
                                    "skin": "#111111",
                                    "hair": "#222222",
                                    "shirt": "#333333"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("길동"))
                .andExpect(jsonPath("$.data.avatarColors.shirt").value("#333333"));
    }

    @Test
    @DisplayName("내 프로필 수정 - 인증 없을 때 401")
    void updateMyProfile_noAuth_returns401() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
