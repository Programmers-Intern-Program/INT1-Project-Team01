package back.domain.agent.controller;

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

import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.AgentInfoRes;
import back.domain.agent.dto.response.AgentSkillFileSyncRes;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentSkillSyncStatus;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.service.AgentQueryService;
import back.domain.agent.service.AgentProvisioningService;
import back.global.security.AuthenticatedMember;
import back.testUtil.WebMvcTestSupport;

@WebMvcTest(AgentController.class)
class AgentControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private AgentProvisioningService agentProvisioningService;

    @MockitoBean
    private AgentQueryService agentQueryService;

    @Test
    @DisplayName("Agent 생성 요청이 유효하면 201 Created와 생성 결과를 반환한다")
    void createAgent_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 10L;
        OpenClawAgentCreateRes response = new OpenClawAgentCreateRes(
                100L,
                workspaceId,
                "Backend Agent",
                AgentCategory.BACKEND,
                "openclaw-agent-1",
                "~/.openclaw/workspace-1",
                AgentStatus.READY,
                null,
                List.of(new AgentSkillFileSyncRes(200L, "AGENTS.md", AgentSkillSyncStatus.SYNCED, null)));
        given(agentProvisioningService.createAgent(
                        eq(workspaceId), eq(memberId), any(OpenClawAgentCreateReq.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agents", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new OpenClawAgentCreateReq(
                                "Backend Agent",
                                AgentCategory.BACKEND,
                                null,
                                "tool",
                                List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Agent 생성 요청이 처리되었습니다."))
                .andExpect(jsonPath("$.data.agentId").value(100L))
                .andExpect(jsonPath("$.data.category").value("BACKEND"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.skillFiles[0].syncStatus").value("SYNCED"));
    }

    @Test
    @DisplayName("Agent 목록 조회 요청이 유효하면 200 OK와 Agent 목록을 반환한다")
    void listAgents_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 10L;
        AgentInfoRes response = new AgentInfoRes(
                100L,
                workspaceId,
                "Backend Agent",
                AgentCategory.BACKEND,
                "openclaw-agent-1",
                "~/.openclaw/workspace-1",
                AgentStatus.READY,
                null,
                memberId,
                null,
                null,
                List.of(new AgentSkillFileSyncRes(200L, "AGENTS.md", AgentSkillSyncStatus.SYNCED, null)));
        given(agentQueryService.listAgents(workspaceId, memberId)).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/agents", workspaceId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].agentId").value(100L))
                .andExpect(jsonPath("$.data[0].category").value("BACKEND"))
                .andExpect(jsonPath("$.data[0].skillFiles[0].fileName").value("AGENTS.md"));
    }

    @Test
    @DisplayName("Agent 상세 조회 요청이 유효하면 200 OK와 Agent 상세 정보를 반환한다")
    void getAgent_success() throws Exception {
        // given
        Long workspaceId = 1L;
        Long memberId = 10L;
        Long agentId = 100L;
        AgentInfoRes response = new AgentInfoRes(
                agentId,
                workspaceId,
                "Backend Agent",
                AgentCategory.BACKEND,
                "openclaw-agent-1",
                "~/.openclaw/workspace-1",
                AgentStatus.READY,
                null,
                memberId,
                null,
                null,
                List.of());
        given(agentQueryService.getAgent(workspaceId, memberId, agentId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/agents/{agentId}", workspaceId, agentId)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent 상세 조회 성공"))
                .andExpect(jsonPath("$.data.agentId").value(agentId))
                .andExpect(jsonPath("$.data.name").value("Backend Agent"))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    @DisplayName("Agent 이름이 공백이면 400 Bad Request를 반환한다")
    void createAgent_blankName_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agents", 1L)
                        .with(csrf())
                        .with(authentication(createTestAuthentication(10L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken createTestAuthentication(Long memberId) {
        AuthenticatedMember authenticatedMember = new AuthenticatedMember(memberId, "ADMIN");
        return new UsernamePasswordAuthenticationToken(
                authenticatedMember, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
