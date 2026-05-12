package back.domain.workspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import back.domain.workspace.service.WorkspacePresenceService;
import back.global.security.AuthenticatedMember;
import back.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WorkspacePresenceControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private WorkspacePresenceService workspacePresenceService;

    private MockMvc mockMvc;
    private String accessToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        accessToken = jwtTokenProvider.generateAccessToken(1L, "test@test.com", "USER");
    }

    @Test
    @DisplayName("Presence SSE 스트림 연결 성공")
    void stream_success() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(workspacePresenceService.stream(eq(1L), anyString(), any(), any())).willReturn(emitter);

        mockMvc.perform(get("/api/v1/workspaces/1/presence/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(workspacePresenceService).stream(eq(1L), anyString(), any(), any());
    }

    @Test
    @DisplayName("Presence 위치 갱신 성공")
    void updateMyPosition_success() throws Exception {
        doNothing().when(workspacePresenceService).updateMyPosition(anyLong(), any(AuthenticatedMember.class), any());

        mockMvc.perform(patch("/api/v1/workspaces/1/presence/me/position")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":{\"left\":\"10px\",\"top\":\"20px\"},\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        verify(workspacePresenceService).updateMyPosition(eq(1L), any(AuthenticatedMember.class), any());
    }

    @Test
    @DisplayName("Presence 위치 갱신 - 인증 없으면 401")
    void updateMyPosition_noAuth_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/workspaces/1/presence/me/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":{\"left\":\"10px\",\"top\":\"20px\"}}"))
                .andExpect(status().isUnauthorized());
    }
}
