package back.domain.workspace.controller;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
import back.domain.workspace.enums.WorkspaceInviteStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.service.WorkspaceService;
import back.global.security.JwtTokenProvider;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WorkspaceInviteControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private WorkspaceService workspaceService;

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
    @DisplayName("초대 조회 성공 - 인증 불필요")
    void getInviteInfo_success_withoutAuth() throws Exception {
        // given
        WorkspaceInvitePreviewRes response = new WorkspaceInvitePreviewRes(
                1L,
                "테스트 워크스페이스",
                WorkspaceMemberRole.MEMBER,
                LocalDateTime.now().plusDays(7),
                WorkspaceInviteStatus.PENDING,
                false);
        given(workspaceService.getInviteInfo(anyString())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/invites/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceName").value("테스트 워크스페이스"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("초대 수락 성공")
    void acceptInvite_success() throws Exception {
        // given
        doNothing().when(workspaceService).acceptInvite(anyString(), anyLong());

        // when & then
        mockMvc.perform(post("/api/v1/invites/token/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("초대 수락 성공"));
    }

    @Test
    @DisplayName("초대 수락 - 인증 없을 때 401")
    void acceptInvite_noAuth_returns401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/invites/token/accept"))
                .andExpect(status().isUnauthorized());
    }
}
