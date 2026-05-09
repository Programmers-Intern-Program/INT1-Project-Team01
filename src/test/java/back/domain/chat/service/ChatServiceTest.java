package back.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import back.domain.agent.entity.Agent;
import back.domain.agent.repository.AgentRepository;
import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.entity.ChatMessage;
import back.domain.chat.entity.ChatMessageRole;
import back.domain.chat.entity.ChatSession;
import back.domain.chat.entity.ChatSessionStatus;
import back.domain.chat.repository.ChatMessageRepository;
import back.domain.chat.repository.ChatSessionRepository;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.task.repository.TaskRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @MockitoBean
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @MockitoBean
    private ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;

    private OpenClawGatewayClient openClawGatewayClient;
    private Long workspaceId;
    private Long memberId;
    private Long agentId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(), "test-" + UUID.randomUUID() + "@test.com", "테스트 멤버"));
        Workspace workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "테스트용 워크스페이스입니다.", member));
        Agent agent = Agent.create(workspace, "테스트 Agent", "~/.openclaw/workspace-1", member.getId());
        agent.markOpenClawCreated("openclaw-agent-1");
        agent.markReady();
        Agent savedAgent = agentRepository.save(agent);
        workspaceId = workspace.getId();
        memberId = member.getId();
        agentId = savedAgent.getId();

        openClawGatewayClient = mock(OpenClawGatewayClient.class);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(workspaceGatewayBindingService.getConnectionContext(workspaceId))
                .willReturn(new OpenClawGatewayConnectionContext("ws://127.0.0.1:18789", "gateway-token"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(new OpenClawChatResult("gateway-session", "Agent 응답입니다."));
    }

    @Test
    @DisplayName("일반 채팅은 Task를 만들지 않고 ChatSession 메시지로 저장한다")
    void sendMessage_generalChatStoresSessionMessagesWithoutTask() {
        // given
        ChatMessageSendRequest request =
                new ChatMessageSendRequest("일반 대화야", agentId, null, null, null, null, false);

        // when
        ChatMessageSendResponse response = chatService.sendMessage(workspaceId, request);

        // then
        assertThat(response.chatSessionId()).isNotNull();
        assertThat(response.taskId()).isNull();
        assertThat(response.assignedAgentId()).isEqualTo(agentId);
        assertThat(response.finalText()).isEqualTo("Agent 응답입니다.");
        assertThat(response.messages())
                .extracting(ChatMessageResponse::role, ChatMessageResponse::content)
                .containsExactly(
                        tuple(ChatMessageRole.USER, "일반 대화야"),
                        tuple(ChatMessageRole.ASSISTANT, "Agent 응답입니다."));
        assertThat(taskRepository.findByWorkspaceId(workspaceId, PageRequest.of(0, 10)).getContent()).isEmpty();
        verify(openClawGatewayClient).connect(any(OpenClawGatewayConnectionContext.class));
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("CHAT intent 응답은 message만 일반 채팅으로 저장한다")
    void sendMessage_chatIntentStoresMessageOnly() {
        // given
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(new OpenClawChatResult(
                        "gateway-session",
                        """
                                {
                                  "intent": "CHAT",
                                  "message": "일반 채팅 응답입니다."
                                }
                                """));
        ChatMessageSendRequest request =
                new ChatMessageSendRequest("일반 대화야", agentId, null, null, null, null, false);

        // when
        ChatMessageSendResponse response = chatService.sendMessage(workspaceId, request);

        // then
        assertThat(response.taskId()).isNull();
        assertThat(response.finalText()).isEqualTo("일반 채팅 응답입니다.");
        assertThat(response.messages())
                .extracting(ChatMessageResponse::role, ChatMessageResponse::content)
                .containsExactly(
                        tuple(ChatMessageRole.USER, "일반 대화야"),
                        tuple(ChatMessageRole.ASSISTANT, "일반 채팅 응답입니다."));
        assertThat(taskRepository.findByWorkspaceId(workspaceId, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("TASK intent 응답은 Task를 생성하고 채팅 메시지에 연결한다")
    void sendMessage_taskIntentCreatesTaskAndLinksMessages() {
        // given
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(new OpenClawChatResult(
                        "gateway-session",
                        """
                                {
                                  "intent": "TASK",
                                  "message": "작업을 시작하겠습니다.",
                                  "task": {
                                    "title": "로그인 API 구현",
                                    "description": "로그인 API와 테스트 코드를 작성합니다.",
                                    "taskType": "FEATURE_IMPLEMENTATION",
                                    "priority": "HIGH",
                                    "repositoryId": 7,
                                    "createPr": true
                                  }
                                }
                                """));
        ChatMessageSendRequest request =
                new ChatMessageSendRequest("로그인 API 구현해줘", agentId, null, null, null, null, false);

        // when
        ChatMessageSendResponse response = chatService.sendMessage(workspaceId, request);

        // then
        assertThat(response.taskId()).isNotNull();
        assertThat(response.taskStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.finalText()).isEqualTo("작업을 시작하겠습니다.");
        assertThat(response.messages())
                .extracting(ChatMessageResponse::role, ChatMessageResponse::content, ChatMessageResponse::taskId)
                .containsExactly(
                        tuple(ChatMessageRole.USER, "로그인 API 구현해줘", response.taskId()),
                        tuple(ChatMessageRole.ASSISTANT, "작업을 시작하겠습니다.", response.taskId()));
        Task task = taskRepository.findByIdAndWorkspaceId(response.taskId(), workspaceId).orElseThrow();
        assertThat(task.getTitle()).isEqualTo("로그인 API 구현");
        assertThat(task.getDescription()).isEqualTo("로그인 API와 테스트 코드를 작성합니다.");
        assertThat(task.getTaskType()).isEqualTo(TaskType.FEATURE_IMPLEMENTATION);
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.getRepositoryId()).isEqualTo(7L);
        verify(chatTaskExecutionDispatcher).run(workspaceId, response.taskId(), true);
    }

    @Test
    @DisplayName("같은 ChatSession으로 보낸 일반 채팅은 같은 OpenClaw sessionKey를 재사용한다")
    void sendMessage_existingSessionReusesOpenClawSessionKey() {
        // given
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(
                        new OpenClawChatResult("gateway-session", "첫 응답"),
                        new OpenClawChatResult("gateway-session", "두 번째 응답"));
        ChatMessageSendResponse first = chatService.sendMessage(
                workspaceId,
                new ChatMessageSendRequest("첫 메시지", agentId, null, null, null, null, false));

        // when
        ChatMessageSendResponse second = chatService.sendMessage(
                workspaceId,
                new ChatMessageSendRequest("두 번째 메시지", agentId, null, null, null, null, false, first.chatSessionId()));

        // then
        ArgumentCaptor<OpenClawChatCommand> commandCaptor = ArgumentCaptor.forClass(OpenClawChatCommand.class);
        verify(openClawGatewayClient, times(2)).sendChat(commandCaptor.capture());
        List<OpenClawChatCommand> commands = commandCaptor.getAllValues();
        assertThat(commands.get(0).sessionKey()).isEqualTo(commands.get(1).sessionKey());
        assertThat(second.chatSessionId()).isEqualTo(first.chatSessionId());
        assertThat(second.messages())
                .extracting(ChatMessageResponse::role, ChatMessageResponse::content)
                .containsExactly(
                        tuple(ChatMessageRole.USER, "첫 메시지"),
                        tuple(ChatMessageRole.ASSISTANT, "첫 응답"),
                        tuple(ChatMessageRole.USER, "두 번째 메시지"),
                        tuple(ChatMessageRole.ASSISTANT, "두 번째 응답"));
    }

    @Test
    @DisplayName("채팅 세션 메시지를 polling 방식으로 조회한다")
    void getSessionMessages_returnsChatMessages() {
        // given
        ChatMessageSendResponse sent = chatService.sendMessage(
                workspaceId,
                new ChatMessageSendRequest("조회할 메시지", agentId, null, null, null, null, false));

        // when
        List<ChatMessageResponse> messages = chatService.getSessionMessages(workspaceId, sent.chatSessionId());

        // then
        assertThat(messages)
                .extracting(ChatMessageResponse::role, ChatMessageResponse::content)
                .containsExactly(
                        tuple(ChatMessageRole.USER, "조회할 메시지"),
                        tuple(ChatMessageRole.ASSISTANT, "Agent 응답입니다."));
    }

    @Test
    @DisplayName("기존 ChatSession은 같은 Agent로만 재사용할 수 있다")
    void sendMessage_agentMismatch_throwsException() {
        // given
        ChatMessageSendResponse sent = chatService.sendMessage(
                workspaceId,
                new ChatMessageSendRequest("기존 세션 생성", agentId, null, null, null, null, false));
        Long anotherAgentId = createReadyAgent("다른 Agent", "openclaw-agent-2");
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "다른 Agent로 같은 세션 사용",
                anotherAgentId,
                null,
                null,
                null,
                null,
                false,
                sent.chatSessionId());

        // when & then
        assertThatThrownBy(() -> chatService.sendMessage(workspaceId, request))
                .isInstanceOfSatisfying(ServiceException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST_STATE);
                    assertThat(exception.getClientMessage()).contains("채팅 세션의 Agent");
                });
    }

    @Test
    @DisplayName("OpenClaw 호출 실패 시 시스템 메시지로 실패 사유를 기록한다")
    void sendMessage_openClawFailure_recordsSystemMessage() {
        // given
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willThrow(OpenClawGatewayException.rpcTimeout("chat.send", "request-1"));
        ChatMessageSendRequest request =
                new ChatMessageSendRequest("실패도 기록해줘", agentId, null, null, null, null, false);

        // when & then
        assertThatThrownBy(() -> chatService.sendMessage(workspaceId, request))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getClientMessage()).contains("요청 시간이 초과"));

        List<ChatSession> sessions = chatSessionRepository
                .findByWorkspaceIdAndAgentIdAndStatusOrderByLastMessageAtDescIdDesc(
                        workspaceId, agentId, ChatSessionStatus.ACTIVE);
        assertThat(sessions).hasSize(1);
        List<ChatMessage> messages = chatMessageRepository
                .findByWorkspaceIdAndChatSessionIdOrderByCreatedAtAscIdAsc(workspaceId, sessions.get(0).getId());
        assertThat(messages)
                .extracting(ChatMessage::getRole, ChatMessage::getContent)
                .containsExactly(
                        tuple(ChatMessageRole.USER, "실패도 기록해줘"),
                        tuple(ChatMessageRole.SYSTEM, "OpenClaw Gateway 요청 시간이 초과되었습니다."));
    }

    private Long createReadyAgent(String name, String openClawAgentId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        Agent agent = Agent.create(workspace, name, "~/.openclaw/workspace-1", memberId);
        agent.markOpenClawCreated(openClawAgentId);
        agent.markReady();
        return agentRepository.save(agent).getId();
    }
}
