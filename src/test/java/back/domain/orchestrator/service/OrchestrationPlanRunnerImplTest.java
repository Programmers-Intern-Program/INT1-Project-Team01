package back.domain.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.artifact.dto.StoredArtifactFile;
import back.domain.artifact.service.WorkspaceArtifactStorage;
import back.domain.chat.entity.ChatMessage;
import back.domain.chat.entity.ChatSession;
import back.domain.chat.entity.ChatSessionSource;
import back.domain.chat.repository.ChatMessageRepository;
import back.domain.chat.repository.ChatSessionRepository;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.service.AgentExecutionResult;
import back.domain.execution.service.AgentExecutionResultParser;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.member.entity.Member;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.entity.OrchestrationPlanStatus;
import back.domain.orchestrator.entity.OrchestrationPlanStep;
import back.domain.orchestrator.entity.OrchestrationPlanStepStatus;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.domain.orchestrator.repository.OrchestrationPlanStepRepository;
import back.domain.slack.event.SlackReplyRequestedEvent;
import back.domain.workspace.entity.Workspace;

@ExtendWith(MockitoExtension.class)
class OrchestrationPlanRunnerImplTest {

    @Mock
    private TransactionOperations transactionOperations;

    @Mock
    private OrchestrationPlanRepository orchestrationPlanRepository;

    @Mock
    private OrchestrationPlanStepRepository orchestrationPlanStepRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @Mock
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Mock
    private OpenClawGatewayClient openClawGatewayClient;

    @Mock
    private AgentExecutionResultParser agentExecutionResultParser;

    @Mock
    private WorkspaceArtifactStorage workspaceArtifactStorage;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrchestrationPlanRunnerImpl orchestrationPlanRunner;
    private OrchestrationPlan plan;
    private ChatSession chatSession;
    private OrchestrationPlanStep backendStep;
    private OrchestrationPlanStep frontendStep;
    private Agent backendAgent;
    private Agent frontendAgent;

    @BeforeEach
    void setUp() {
        orchestrationPlanRunner = new OrchestrationPlanRunnerImpl(
                transactionOperations,
                orchestrationPlanRepository,
                orchestrationPlanStepRepository,
                agentRepository,
                workspaceGatewayBindingService,
                openClawGatewayClientFactory,
                agentExecutionResultParser,
                workspaceArtifactStorage,
                chatSessionRepository,
                chatMessageRepository,
                eventPublisher);
        given(transactionOperations.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        plan = OrchestrationPlan.create(1L, 10L, 100L, 1000L, "회원가입 구현 계획", "{}");
        ReflectionTestUtils.setField(plan, "id", 500L);
        chatSession = ChatSession.start(1L, 100L, ChatSessionSource.WEB, null, "chat-session-key");
        ReflectionTestUtils.setField(chatSession, "id", 10L);
        backendStep = OrchestrationPlanStep.create(
                plan,
                2,
                "backend-1",
                null,
                null,
                AgentCategory.BACKEND,
                "회원가입 API 구현",
                "회원가입 API를 구현하세요.",
                List.of());
        frontendStep = OrchestrationPlanStep.create(
                plan,
                1,
                "frontend-1",
                null,
                null,
                AgentCategory.FRONTEND,
                "회원가입 화면 연동",
                "회원가입 화면을 구현하세요.",
                List.of("backend-1"));
        ReflectionTestUtils.setField(backendStep, "id", 2002L);
        ReflectionTestUtils.setField(frontendStep, "id", 2001L);

        Workspace workspace = createWorkspace();
        backendAgent = createReadyAgent(workspace, 101L, "backend-agent", AgentCategory.BACKEND, "openclaw-backend");
        frontendAgent = createReadyAgent(
                workspace, 102L, "frontend-agent", AgentCategory.FRONTEND, "openclaw-frontend");

        lenient().when(orchestrationPlanRepository.save(any(OrchestrationPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(orchestrationPlanStepRepository.save(any(OrchestrationPlanStep.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chatSessionRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(chatSession));
        lenient().when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("의존성 순서대로 Worker Agent를 실행하고 각 Step 결과를 저장한다")
    void run_dependencyOrderedSteps_success() {
        // given
        stubPlanAndSteps();
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willReturn(new OpenClawGatewayConnectionContext("ws://localhost:18789", "gateway-token"));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.BACKEND, AgentStatus.READY))
                .willReturn(Optional.of(backendAgent));
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.FRONTEND, AgentStatus.READY))
                .willReturn(Optional.of(frontendAgent));
        given(workspaceArtifactStorage.resolveProjectRoot(1L))
                .willReturn(Path.of("/tmp/ai-office/workspaces/1/project"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(
                        new OpenClawChatResult("backend-session", "backend final"),
                        new OpenClawChatResult("frontend-session", "frontend final"));
        AgentExecutionResult backendResult = new AgentExecutionResult(
                new AgentReportSaveRequest("COMPLETED", "백엔드 완료", "API 구현 완료", null),
                List.of(),
                List.of(new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}")),
                List.of("API 스펙 재확인 필요"),
                List.of("프론트 연동 확인"));
        AgentExecutionResult frontendResult = new AgentExecutionResult(
                new AgentReportSaveRequest("COMPLETED", "프론트 완료", "화면 구현 완료", null),
                List.of());
        given(agentExecutionResultParser.parse("backend final")).willReturn(backendResult);
        given(agentExecutionResultParser.parse("frontend final")).willReturn(frontendResult);
        given(workspaceArtifactStorage.storeFiles(1L, backendResult.files()))
                .willReturn(List.of(new StoredArtifactFile("src/main/java/App.java", 12)));

        // when
        orchestrationPlanRunner.run(1L, 500L);

        // then
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.COMPLETED);
        assertThat(backendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.COMPLETED);
        assertThat(backendStep.getResolvedAgentId()).isEqualTo(101L);
        assertThat(backendStep.getResultStatus()).isEqualTo("COMPLETED");
        assertThat(backendStep.getResultSummary()).isEqualTo("백엔드 완료");
        assertThat(backendStep.getResultFilePaths()).isEqualTo("src/main/java/App.java");
        assertThat(backendStep.getResultRisks()).isEqualTo("API 스펙 재확인 필요");
        assertThat(backendStep.getResultNextActions()).isEqualTo("프론트 연동 확인");
        assertThat(frontendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.COMPLETED);

        ArgumentCaptor<OpenClawChatCommand> commandCaptor = ArgumentCaptor.forClass(OpenClawChatCommand.class);
        verify(openClawGatewayClient, times(2)).sendChat(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(OpenClawChatCommand::openClawAgentId)
                .containsExactly("openclaw-backend", "openclaw-frontend");
        assertThat(commandCaptor.getAllValues().getLast().message())
                .contains("backend-1: 백엔드 완료 files=src/main/java/App.java");
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getOrchestrationPlanId()).isEqualTo(500L);
        assertThat(messageCaptor.getValue().getContent())
                .contains("Orchestration 실행이 완료되었습니다.", "- 상태: COMPLETED", "backend-1: 백엔드 완료");
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Slack 세션에서 시작된 Orchestration은 최종 요약을 Slack reply 이벤트로 발행한다")
    void run_slackSessionPublishesFinalReply() {
        // given
        ChatSession slackSession =
                ChatSession.start(1L, 100L, ChatSessionSource.SLACK, "T123:C123:999.000", "slack-session-key");
        ReflectionTestUtils.setField(slackSession, "id", 10L);
        given(chatSessionRepository.findByIdAndWorkspaceId(10L, 1L)).willReturn(Optional.of(slackSession));
        stubPlanAndSteps();
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willReturn(new OpenClawGatewayConnectionContext("ws://localhost:18789", "gateway-token"));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.BACKEND, AgentStatus.READY))
                .willReturn(Optional.of(backendAgent));
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.FRONTEND, AgentStatus.READY))
                .willReturn(Optional.of(frontendAgent));
        given(workspaceArtifactStorage.resolveProjectRoot(1L))
                .willReturn(Path.of("/tmp/ai-office/workspaces/1/project"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(
                        new OpenClawChatResult("backend-session", "backend final"),
                        new OpenClawChatResult("frontend-session", "frontend final"));
        given(agentExecutionResultParser.parse("backend final"))
                .willReturn(new AgentExecutionResult(
                        new AgentReportSaveRequest("COMPLETED", "백엔드 완료", "API 구현 완료", null),
                        List.of()));
        given(agentExecutionResultParser.parse("frontend final"))
                .willReturn(new AgentExecutionResult(
                        new AgentReportSaveRequest("COMPLETED", "프론트 완료", "화면 구현 완료", null),
                        List.of()));

        // when
        orchestrationPlanRunner.run(1L, 500L);

        // then
        ArgumentCaptor<SlackReplyRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(SlackReplyRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sourceRef()).isEqualTo("T123:C123:999.000");
        assertThat(eventCaptor.getValue().message()).contains("Orchestration 실행이 완료되었습니다.");
        assertThat(eventCaptor.getValue().deduplicationKey()).isEqualTo("slack-orchestration-plan-500-COMPLETED");
    }

    @Test
    @DisplayName("Worker Agent 결과가 실패이면 Plan을 실패 처리하고 다음 Step은 실행하지 않는다")
    void run_failedStep_stopsRemainingSteps() {
        // given
        stubPlanAndSteps();
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willReturn(new OpenClawGatewayConnectionContext("ws://localhost:18789", "gateway-token"));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.BACKEND, AgentStatus.READY))
                .willReturn(Optional.of(backendAgent));
        given(workspaceArtifactStorage.resolveProjectRoot(1L))
                .willReturn(Path.of("/tmp/ai-office/workspaces/1/project"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(new OpenClawChatResult("backend-session", "backend final"));
        given(agentExecutionResultParser.parse("backend final"))
                .willReturn(new AgentExecutionResult(
                        new AgentReportSaveRequest("FAILED", "백엔드 실패", "테스트 실패", null),
                        List.of()));

        // when
        orchestrationPlanRunner.run(1L, 500L);

        // then
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.FAILED);
        assertThat(backendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.FAILED);
        assertThat(backendStep.getFailureReason()).isEqualTo("테스트 실패");
        assertThat(frontendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.PENDING);
        verify(openClawGatewayClient, times(1)).sendChat(any(OpenClawChatCommand.class));
        verify(agentRepository, never())
                .findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.FRONTEND, AgentStatus.READY);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent())
                .contains("Orchestration 실행이 실패했습니다.", "- 실패 step: backend-1", "- 사유: 테스트 실패");
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Worker Agent chat.send timeout은 실행 단계에 맞는 실패 사유로 저장한다")
    void run_workerChatTimeout_marksFailedWithWorkerMessage() {
        // given
        stubPlanAndSteps();
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willReturn(new OpenClawGatewayConnectionContext("ws://localhost:18789", "gateway-token"));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.BACKEND, AgentStatus.READY))
                .willReturn(Optional.of(backendAgent));
        given(workspaceArtifactStorage.resolveProjectRoot(1L))
                .willReturn(Path.of("/tmp/ai-office/workspaces/1/project"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willThrow(OpenClawGatewayException.rpcTimeout("chat.send", "req-1"));

        // when
        orchestrationPlanRunner.run(1L, 500L);

        // then
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.FAILED);
        assertThat(backendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.FAILED);
        assertThat(backendStep.getFailureReason())
                .contains("Worker Agent 응답 시간이 초과되었습니다.", "OPENCLAW_GATEWAY_CHAT_TIMEOUT");
        assertThat(frontendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.PENDING);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent())
                .contains("- 실패 step: backend-1", "Worker Agent 응답 시간이 초과되었습니다.");
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Worker Agent 결과가 취소이면 Plan을 취소 처리하고 다음 Step은 실행하지 않는다")
    void run_canceledStep_cancelsPlanAndStopsRemainingSteps() {
        // given
        stubPlanAndSteps();
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willReturn(new OpenClawGatewayConnectionContext("ws://localhost:18789", "gateway-token"));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.BACKEND, AgentStatus.READY))
                .willReturn(Optional.of(backendAgent));
        given(workspaceArtifactStorage.resolveProjectRoot(1L))
                .willReturn(Path.of("/tmp/ai-office/workspaces/1/project"));
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willReturn(new OpenClawChatResult("backend-session", "backend final"));
        given(agentExecutionResultParser.parse("backend final"))
                .willReturn(new AgentExecutionResult(
                        new AgentReportSaveRequest("CANCELED", "백엔드 취소", "사용자 취소", null),
                        List.of()));

        // when
        orchestrationPlanRunner.run(1L, 500L);

        // then
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.CANCELED);
        assertThat(backendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.CANCELED);
        assertThat(backendStep.getFailureReason()).isEqualTo("사용자 취소");
        assertThat(frontendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.PENDING);
        verify(openClawGatewayClient, times(1)).sendChat(any(OpenClawChatCommand.class));
        verify(agentRepository, never())
                .findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.FRONTEND, AgentStatus.READY);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent())
                .contains("Orchestration 실행이 취소되었습니다.", "- 취소 step: backend-1", "- 사유: 사용자 취소");
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("자동 선택된 Worker Agent의 OpenClaw Agent ID가 비어 있으면 실행하지 않고 실패 처리한다")
    void run_autoSelectedAgentWithBlankOpenClawAgentId_marksFailed() {
        // given
        stubPlanAndSteps();
        ReflectionTestUtils.setField(backendAgent, "openClawAgentId", " ");
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willReturn(new OpenClawGatewayConnectionContext("ws://localhost:18789", "gateway-token"));
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(agentRepository.findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentCategory.BACKEND, AgentStatus.READY))
                .willReturn(Optional.of(backendAgent));

        // when
        orchestrationPlanRunner.run(1L, 500L);

        // then
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.FAILED);
        assertThat(backendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.FAILED);
        assertThat(backendStep.getFailureReason()).isEqualTo("Worker Agent가 READY 상태가 아닙니다.");
        assertThat(frontendStep.getStatus()).isEqualTo(OrchestrationPlanStepStatus.PENDING);
        verify(openClawGatewayClient, never()).sendChat(any(OpenClawChatCommand.class));
        verify(openClawGatewayClient).close();
    }

    private void stubPlanAndSteps() {
        given(orchestrationPlanRepository.findByIdAndWorkspaceId(500L, 1L)).willReturn(Optional.of(plan));
        given(orchestrationPlanStepRepository.findByPlanIdOrderBySequenceNoAscIdAsc(500L))
                .willReturn(List.of(frontendStep, backendStep));
        given(orchestrationPlanStepRepository.findById(backendStep.getId())).willReturn(Optional.of(backendStep));
        lenient()
                .when(orchestrationPlanStepRepository.findById(frontendStep.getId()))
                .thenReturn(Optional.of(frontendStep));
    }

    private Workspace createWorkspace() {
        Member member = Member.createUser("sub", "test@test.com", "테스트 멤버");
        ReflectionTestUtils.setField(member, "id", 10L);
        Workspace workspace = Workspace.create("AI Office", "테스트 워크스페이스", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        return workspace;
    }

    private Agent createReadyAgent(
            Workspace workspace,
            Long agentId,
            String name,
            AgentCategory category,
            String openClawAgentId) {
        Agent agent = Agent.create(workspace, name, category, "~/.openclaw/workspace-1", 10L);
        ReflectionTestUtils.setField(agent, "id", agentId);
        agent.markOpenClawCreated(openClawAgentId);
        agent.markReady();
        return agent;
    }
}
