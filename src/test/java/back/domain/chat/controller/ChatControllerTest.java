package back.domain.chat.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import back.domain.agent.entity.Agent;
import back.domain.agent.repository.AgentRepository;
import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class ChatControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private AgentRepository agentRepository;

    @MockitoBean
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @MockitoBean
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    private ObjectMapper objectMapper;
    private MockMvc mockMvc;
    private OpenClawGatewayClient openClawGatewayClient;
    private Long workspaceId;
    private Long agentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();

        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(), "test-" + UUID.randomUUID() + "@test.com", "테스트 멤버"));
        Workspace workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "테스트용 워크스페이스입니다.", member));
        Agent agent = Agent.create(workspace, "테스트 Agent", "~/.openclaw/workspace-1", member.getId());
        agent.markOpenClawCreated("openclaw-agent-1");
        agent.markReady();
        Agent savedAgent = agentRepository.save(agent);
        workspaceId = workspace.getId();
        agentId = savedAgent.getId();

        openClawGatewayClient = mock(OpenClawGatewayClient.class);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(workspaceGatewayBindingService.getConnectionContext(workspaceId))
                .willReturn(new OpenClawGatewayConnectionContext("ws://127.0.0.1:18789", "gateway-token"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(new OpenClawChatResult("gateway-session", "Agent 응답입니다."));
    }

    @Test
    @DisplayName("프론트 채팅 요청으로 일반 Agent 대화를 시작하고 메시지를 반환한다")
    void sendMessage() throws Exception {
        // given
        ChatMessageSendRequest request =
                new ChatMessageSendRequest("프론트 채팅으로 대화해줘", agentId, null, null, null, null, false);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatSessionId").exists())
                .andExpect(jsonPath("$.taskId").doesNotExist())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.assignedAgentId").value(agentId))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("프론트 채팅으로 대화해줘"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].content").value("Agent 응답입니다."));
    }

    @Test
    @DisplayName("프론트 채팅 요청의 필수값을 검증한다")
    void validateSendMessageRequest() throws Exception {
        ChatMessageSendRequest request = new ChatMessageSendRequest(" ", 0L, null, null, null, null, false);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기존 채팅 세션에 메시지를 보내면 신규 메시지만 반환한다")
    void sendMessage_existingSessionReturnsNewMessagesOnly() throws Exception {
        // given
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(
                        new OpenClawChatResult("gateway-session", "첫 응답입니다."),
                        new OpenClawChatResult("gateway-session", "두 번째 응답입니다."));
        ChatMessageSendRequest firstRequest =
                new ChatMessageSendRequest("첫 메시지", agentId, null, null, null, null, false);
        String firstResponseBody = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long chatSessionId = objectMapper.readTree(firstResponseBody).get("chatSessionId").asLong();
        ChatMessageSendRequest secondRequest =
                new ChatMessageSendRequest(
                        "두 번째 메시지",
                        agentId,
                        null,
                        null,
                        null,
                        null,
                        false,
                        chatSessionId);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatSessionId").value(chatSessionId))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("두 번째 메시지"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].content").value("두 번째 응답입니다."));
    }

    @Test
    @DisplayName("채팅 메시지를 polling 방식으로 조회할 수 있다")
    void getSessionMessages() throws Exception {
        // given
        Long chatSessionId = sendChatMessageAndGetSessionId();

        // when & then
        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/chat/sessions/{chatSessionId}/messages",
                        workspaceId,
                        chatSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatSessionId").value(chatSessionId))
                .andExpect(jsonPath("$.messages.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.nextCursor").exists())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @DisplayName("채팅 메시지 polling은 afterMessageId 이후 신규 메시지만 조회한다")
    void getSessionMessages_afterMessageIdReturnsNewMessagesOnly() throws Exception {
        // given
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(
                        new OpenClawChatResult("gateway-session", "첫 응답입니다."),
                        new OpenClawChatResult("gateway-session", "두 번째 응답입니다."));
        ChatMessageSendRequest firstRequest =
                new ChatMessageSendRequest("첫 메시지", agentId, null, null, null, null, false);
        String firstResponseBody = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode firstResponse = objectMapper.readTree(firstResponseBody);
        Long chatSessionId = firstResponse.get("chatSessionId").asLong();
        Long afterMessageId = firstResponse.get("messages").get(1).get("messageId").asLong();
        ChatMessageSendRequest secondRequest =
                new ChatMessageSendRequest(
                        "두 번째 메시지",
                        agentId,
                        null,
                        null,
                        null,
                        null,
                        false,
                        chatSessionId);
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk());

        // when & then
        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/chat/sessions/{chatSessionId}/messages",
                        workspaceId,
                        chatSessionId)
                        .queryParam("afterMessageId", String.valueOf(afterMessageId))
                        .queryParam("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatSessionId").value(chatSessionId))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("두 번째 메시지"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].content").value("두 번째 응답입니다."))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    private Long sendChatMessageAndGetSessionId() throws Exception {
        ChatMessageSendRequest request =
                new ChatMessageSendRequest("polling 조회용 메시지", agentId, null, null, null, null, false);
        String responseBody = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.get("chatSessionId").asLong();
    }
}
