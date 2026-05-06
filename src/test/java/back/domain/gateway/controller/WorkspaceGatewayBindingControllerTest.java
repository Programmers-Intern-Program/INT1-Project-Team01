package back.domain.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import back.testUtil.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.entity.GatewayMode;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.global.security.AuthenticatedMember;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(WorkspaceGatewayBindingController.class)
class WorkspaceGatewayBindingControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

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