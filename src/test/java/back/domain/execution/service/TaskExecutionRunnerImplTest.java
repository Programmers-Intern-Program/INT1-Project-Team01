package back.domain.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.member.entity.Member;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class TaskExecutionRunnerImplTest {

    @Mock
    private TransactionOperations transactionOperations;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @Mock
    private OpenClawGatewayClientFactory openClawGatewayClientFactory;

    @Mock
    private OpenClawGatewayClient openClawGatewayClient;

    @Mock
    private TaskExecutionResultRecorder taskExecutionResultRecorder;

    @InjectMocks
    private TaskExecutionRunnerImpl taskExecutionRunner;

    private Workspace workspace;
    private Agent readyAgent;
    private OpenClawGatewayConnectionContext gatewayContext;

    @BeforeEach
    void setUp() {
        Member member = Member.createUser("sub", "test@test.com", "관리자");
        ReflectionTestUtils.setField(member, "id", 10L);
        workspace = Workspace.create("AI Office", "설명", member);
        ReflectionTestUtils.setField(workspace, "id", 1L);

        readyAgent = Agent.create(workspace, "Backend Agent", "~/.openclaw/workspace-1", 10L);
        ReflectionTestUtils.setField(readyAgent, "id", 100L);
        readyAgent.markOpenClawCreated("openclaw-agent-1");
        readyAgent.markReady();
        gatewayContext = new OpenClawGatewayConnectionContext("ws://localhost:34115", "gateway-secret-token");
        ReflectionTestUtils.setField(taskExecutionRunner, "workdirRoot", "/tmp/aioffice/workspaces/");

        given(transactionOperations.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        AtomicLong executionId = new AtomicLong(200L);
        lenient().when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(invocation -> {
            TaskExecution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                ReflectionTestUtils.setField(execution, "id", executionId.getAndIncrement());
            }
            return execution;
        });
    }

    @Test
    @DisplayName("READY Agent를 선택하고 TaskExecution을 RUNNING/SUCCEEDED로 전이하며 chat.send를 호출한다")
    void run_readyAgent_success() {
        // given
        TaskExecutionRunCommand command =
                new TaskExecutionRunCommand(1L, 50L, 9L, "회원가입 API를 리뷰해줘", true);
        OpenClawChatResult chatResult =
                new OpenClawChatResult("agent:openclaw-agent-1:workspace-1-execution-200", "작업을 완료했습니다.");
        AgentExecutionResult agentResult = new AgentExecutionResult(
                new AgentReportSaveRequest("COMPLETED", "작업 완료", "작업을 완료했습니다.", null),
                null);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(agentRepository.findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentStatus.READY))
                .willReturn(Optional.of(readyAgent));
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class))).willReturn(chatResult);
        given(taskExecutionResultRecorder.parse(chatResult)).willReturn(agentResult);

        // when
        TaskExecutionRunResult result = taskExecutionRunner.run(command);

        // then
        assertThat(result.taskExecutionId()).isEqualTo(200L);
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        assertThat(result.agentId()).isEqualTo(100L);
        assertThat(result.workdirPath()).isEqualTo("/tmp/aioffice/workspaces/1/executions/200/repo");
        assertThat(result.openClawSessionKey()).isEqualTo("workspace-1-execution-200");
        assertThat(result.finalText()).isEqualTo("작업을 완료했습니다.");

        ArgumentCaptor<OpenClawChatCommand> commandCaptor = ArgumentCaptor.forClass(OpenClawChatCommand.class);
        verify(openClawGatewayClient).connect(gatewayContext);
        verify(openClawGatewayClient).sendChat(commandCaptor.capture());
        assertThat(commandCaptor.getValue().openClawAgentId()).isEqualTo("openclaw-agent-1");
        assertThat(commandCaptor.getValue().fullSessionKey())
                .isEqualTo("agent:openclaw-agent-1:workspace-1-execution-200");
        assertThat(commandCaptor.getValue().message())
                .contains("taskExecutionId: 200")
                .contains("workdirPath: /tmp/aioffice/workspaces/1/executions/200/repo")
                .contains("createPr: true")
                .contains("Final report must be a JSON object.")
                .contains("Allowed status values: COMPLETED, FAILED, CANCELED.")
                .contains("회원가입 API를 리뷰해줘")
                .doesNotContain("gateway-secret-token");
        verify(taskExecutionResultRecorder).recordResult(any(TaskExecution.class), eq(agentResult));
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("Agent final report가 FAILED이면 chat.send 성공 후에도 TaskExecution을 FAILED로 저장한다")
    void run_agentReportFailed_marksFailed() {
        // given
        TaskExecutionRunCommand command = new TaskExecutionRunCommand(1L, 50L, null, "작업 실행", false);
        OpenClawChatResult chatResult = new OpenClawChatResult("session-1", "final report");
        AgentExecutionResult agentResult = new AgentExecutionResult(
                new AgentReportSaveRequest("FAILED", "작업 실패", "테스트 실패", "테스트 로그를 확인하세요."),
                null);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(agentRepository.findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentStatus.READY))
                .willReturn(Optional.of(readyAgent));
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class))).willReturn(chatResult);
        given(taskExecutionResultRecorder.parse(chatResult)).willReturn(agentResult);

        // when
        TaskExecutionRunResult result = taskExecutionRunner.run(command);

        // then
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("테스트 실패");
        verify(taskExecutionResultRecorder).recordResult(any(TaskExecution.class), eq(agentResult));
        verify(openClawGatewayClient).close();
    }

    @Test
    @DisplayName("READY Agent가 없으면 실행을 생성하지 않고 예외를 던진다")
    void run_noReadyAgent_throwsException() {
        // given
        TaskExecutionRunCommand command = new TaskExecutionRunCommand(1L, 50L, null, "작업 실행", false);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(agentRepository.findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentStatus.READY))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskExecutionRunner.run(command))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(openClawGatewayClientFactory, never()).create();
    }

    @Test
    @DisplayName("Gateway binding이 없으면 TaskExecution을 FAILED로 저장하고 Gateway client를 만들지 않는다")
    void run_missingGatewayBinding_marksFailed() {
        // given
        TaskExecutionRunCommand command = new TaskExecutionRunCommand(1L, 50L, null, "작업 실행", false);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(agentRepository.findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentStatus.READY))
                .willReturn(Optional.of(readyAgent));
        given(workspaceGatewayBindingService.getConnectionContext(1L))
                .willThrow(new ServiceException(
                        CommonErrorCode.NOT_FOUND, "gateway binding not found", "워크스페이스 Gateway 설정이 없습니다."));

        // when
        TaskExecutionRunResult result = taskExecutionRunner.run(command);

        // then
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("워크스페이스 Gateway 설정이 없습니다.");
        verify(taskExecutionResultRecorder).recordFailure(any(TaskExecution.class));
        verify(openClawGatewayClientFactory, never()).create();
    }

    @Test
    @DisplayName("chat.send가 실패하면 TaskExecution을 FAILED로 저장한다")
    void run_sendChatFailed_marksFailed() {
        // given
        TaskExecutionRunCommand command = new TaskExecutionRunCommand(1L, 50L, null, "작업 실행", false);
        given(workspaceRepository.findById(1L)).willReturn(Optional.of(workspace));
        given(agentRepository.findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        1L, AgentStatus.READY))
                .willReturn(Optional.of(readyAgent));
        given(workspaceGatewayBindingService.getConnectionContext(1L)).willReturn(gatewayContext);
        given(openClawGatewayClientFactory.create()).willReturn(openClawGatewayClient);
        given(openClawGatewayClient.sendChat(any(OpenClawChatCommand.class)))
                .willThrow(OpenClawGatewayException.rpcTimeout("chat.send", "req-1"));

        // when
        TaskExecutionRunResult result = taskExecutionRunner.run(command);

        // then
        assertThat(result.status()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("OpenClaw Gateway 요청 시간이 초과되었습니다.");
        verify(taskExecutionResultRecorder).recordFailure(any(TaskExecution.class));
        verify(openClawGatewayClient).close();
    }
}
