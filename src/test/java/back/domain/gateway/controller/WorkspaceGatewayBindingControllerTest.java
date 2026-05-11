package back.domain.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.request.WorkspaceGatewayConnectionTestReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingStatus;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.dto.response.WorkspaceGatewayConnectionTestRes;
import back.domain.gateway.dto.response.WorkspaceGatewayStatusRes;
import back.domain.gateway.entity.GatewayConnectionStatus;
import back.domain.gateway.entity.GatewayMode;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.global.security.AuthenticatedMember;
import back.testUtil.WebMvcTestSupport;

@WebMvcTest(WorkspaceGatewayBindingController.class)
class WorkspaceGatewayBindingControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @Test
    @DisplayName("Gateway 상태 조회 요청이 유효하면 binding 상태를 반환한다")
    void getWorkspaceGatewayStatus_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 10L;
        WorkspaceGatewayStatusRes response = new WorkspaceGatewayStatusRes(
                WorkspaceGatewayBindingStatus.BOUND,
                true,
                100L,
                GatewayMode.EXTERNAL,
                "ws://localhost:34115",
                "gate****oken",
                GatewayConnectionStatus.CONNECTED,
                null,
                null,
                null);
        given(workspaceGatewayBindingService.getWorkspaceGatewayStatus(workspaceId, memberId))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/gateway", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Workspace Gateway 상태를 조회했습니다."))
                .andExpect(jsonPath("$.data.status").value("BOUND"))
                .andExpect(jsonPath("$.data.bound").value(true))
                .andExpect(jsonPath("$.data.bindingId").value(100L))
                .andExpect(jsonPath("$.data.gatewayUrl").value("ws://localhost:34115"))
                .andExpect(jsonPath("$.data.maskedToken").value("gate****oken"))
                .andExpect(jsonPath("$.data.lastStatus").value("CONNECTED"));
    }

    @Test
    @DisplayName("Gateway binding 요청이 유효하면 201 Created와 masking된 token을 반환한다")
    void bindExternalGateway_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 10L;
        WorkspaceGatewayBindingRes response = new WorkspaceGatewayBindingRes(
                100L, workspaceId, GatewayMode.EXTERNAL, "ws://localhost:34115", "gate****oken");
        given(workspaceGatewayBindingService.bindExternalGateway(
                        eq(workspaceId), eq(memberId), any(WorkspaceGatewayBindingReq.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/gateway/binding", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new WorkspaceGatewayBindingReq("http://localhost:34115", "gateway-secret-token"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Workspace Gateway 설정이 저장되었습니다."))
                .andExpect(jsonPath("$.data.id").value(100L))
                .andExpect(jsonPath("$.data.gatewayUrl").value("ws://localhost:34115"))
                .andExpect(jsonPath("$.data.maskedToken").value("gate****oken"));
    }

    @Test
    @DisplayName("Gateway 연결 테스트 요청이 유효하면 연결 상태를 반환한다")
    void testExternalGateway_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 10L;
        WorkspaceGatewayConnectionTestRes response = new WorkspaceGatewayConnectionTestRes(
                GatewayConnectionStatus.CONNECTED,
                true,
                "wss://gateway.example.com",
                "OpenClaw Gateway 연결에 성공했습니다.",
                2);
        given(workspaceGatewayBindingService.testExternalGateway(
                        eq(workspaceId), eq(memberId), any(WorkspaceGatewayConnectionTestReq.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/gateway/connection-test", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new WorkspaceGatewayConnectionTestReq(
                                "https://gateway.example.com", "gateway-secret-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Workspace Gateway 연결 테스트가 완료되었습니다."))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.gatewayUrl").value("wss://gateway.example.com"))
                .andExpect(jsonPath("$.data.agentCount").value(2));
    }

    @Test
    @DisplayName("Gateway URL이 공백이면 400 Bad Request를 반환한다")
    void bindExternalGateway_blankUrl_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/gateway/binding", 1L)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(10L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatewayUrl\":\"\",\"token\":\"gateway-secret-token\"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken createTestAuthentication(Long memberId) {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(memberId, "ADMIN");
        return new UsernamePasswordAuthenticationToken(
                authenticatedMember, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
