package back.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.execution.entity.ExecutionTaskArtifact;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.repository.ExecutionAgentReportRepository;
import back.domain.execution.repository.ExecutionTaskArtifactRepository;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.execution.service.TaskExecutionRunner;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.slack.event.SlackReplyRequestedEvent;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.dto.response.AgentReportResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.dto.response.TaskArtifactResponse;
import back.domain.task.entity.AgentReport;
import back.domain.task.entity.ArtifactType;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskArtifact;
import back.domain.task.entity.TaskMessage;
import back.domain.task.entity.TaskMessageRole;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.task.repository.AgentReportRepository;
import back.domain.task.repository.TaskArtifactRepository;
import back.domain.task.repository.TaskMessageRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
@RecordApplicationEvents
class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRunService taskRunService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TaskExecutionRepository taskExecutionRepository;

    @Autowired
    private ExecutionAgentReportRepository executionAgentReportRepository;

    @Autowired
    private ExecutionTaskArtifactRepository executionTaskArtifactRepository;

    @Autowired
    private AgentReportRepository agentReportRepository;

    @Autowired
    private TaskArtifactRepository taskArtifactRepository;

    @Autowired
    private TaskMessageRepository taskMessageRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private TaskExecutionRunner taskExecutionRunner;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(), "test-" + UUID.randomUUID() + "@test.com", "테스트 멤버"));

        Workspace workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "테스트용 워크스페이스입니다.", member));

        workspaceId = workspace.getId();
    }

    @Test
    @DisplayName("Task를 생성할 수 있다")
    void createTask() {
        // given
        TaskCreateRequest request = createRequest();

        // when
        TaskCreateResponse response = taskService.createTask(workspaceId, request);

        // then
        assertThat(response.taskId()).isNotNull();
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.title()).isEqualTo("PR 리뷰");
        assertThat(response.taskType()).isEqualTo(TaskType.PR_REVIEW);
        assertThat(response.status()).isEqualTo(TaskStatus.REQUESTED);
        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("워크스페이스별 Task 목록을 조회할 수 있다")
    void getTasks() {
        // given
        taskService.createTask(workspaceId, createRequest());

        // when
        Page<TaskListResponse> responses = taskService.getTasks(workspaceId, PageRequest.of(0, 10));

        // then
        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).title()).isEqualTo("PR 리뷰");
        assertThat(responses.getContent().get(0).status()).isEqualTo(TaskStatus.REQUESTED);
    }

    @Test
    @DisplayName("Task 상세 정보를 조회할 수 있다")
    void getTask() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, createRequest());

        // when
        TaskDetailResponse response = taskService.getTask(workspaceId, created.taskId());

        // then
        assertThat(response.taskId()).isEqualTo(created.taskId());
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.title()).isEqualTo("PR 리뷰");
        assertThat(response.description()).isEqualTo("최근 PR 변경사항을 리뷰한다.");
        assertThat(response.taskType()).isEqualTo(TaskType.PR_REVIEW);
        assertThat(response.status()).isEqualTo(TaskStatus.REQUESTED);
    }

    @Test
    @DisplayName("Task 상태를 변경할 수 있다")
    void updateStatus() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, createRequest());

        TaskStatusUpdateRequest request = new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS, "Agent 작업 시작");

        // when
        TaskStatusUpdateResponse response = taskService.updateStatus(workspaceId, created.taskId(), request);

        // then
        assertThat(response.taskId()).isEqualTo(created.taskId());
        assertThat(response.previousStatus()).isEqualTo(TaskStatus.REQUESTED);
        assertThat(response.currentStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Task는 존재하지만 실행 기록이 없으면 빈 로그 목록을 반환한다")
    void getTaskLogsWithoutExecution() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, createRequest());

        // when
        List<TaskLogResponse> responses = taskService.getTaskLogs(workspaceId, created.taskId());

        // then
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Task 리포트 조회는 최신 실행 결과와 산출물을 반환한다")
    void getTaskReportsWithLatestExecutionResult() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, createRequest());
        TaskExecution execution = taskExecutionRepository.save(TaskExecution.queued(
                workspaceId,
                created.taskId(),
                1L,
                "openclaw-agent-1",
                3L,
                "feature/pr-review"));
        ExecutionAgentReport report = executionAgentReportRepository.save(ExecutionAgentReport.create(
                execution.getId(),
                new AgentReportSaveRequest(
                        "COMPLETED",
                        "PR 리뷰가 완료되었습니다.",
                        "입력값 검증 개선 포인트를 확인했습니다.",
                        "DTO validation 추가를 권장합니다.")));
        executionTaskArtifactRepository.save(ExecutionTaskArtifact.create(
                execution.getId(),
                new TaskArtifactSaveRequest(
                        "PR_URL",
                        "생성된 PR",
                        "https://github.com/example/repo/pull/1")));

        // when
        List<AgentReportResponse> responses = taskService.getTaskReports(workspaceId, created.taskId());

        // then
        assertThat(responses).hasSize(1);
        AgentReportResponse response = responses.getFirst();
        assertThat(response.reportId()).isEqualTo(report.getId());
        assertThat(response.taskId()).isEqualTo(created.taskId());
        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.summary()).isEqualTo("PR 리뷰가 완료되었습니다.");
        assertThat(response.artifacts())
                .extracting(TaskArtifactResponse::artifactType, TaskArtifactResponse::name, TaskArtifactResponse::url)
                .containsExactly(tuple(
                        ArtifactType.PR_URL,
                        "생성된 PR",
                        "https://github.com/example/repo/pull/1"));
    }

    @Test
    @DisplayName("Task 리포트 조회는 최신 실행 리포트가 없으면 기존 리포트로 폴백한다")
    void getTaskReportsWithExecutionWithoutReportFallsBackToLegacyReport() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, createRequest());
        taskExecutionRepository.save(TaskExecution.queued(
                workspaceId,
                created.taskId(),
                1L,
                "openclaw-agent-1",
                3L,
                "feature/pr-review"));
        AgentReport legacyReport = agentReportRepository.save(AgentReport.create(
                created.taskId(),
                100L,
                TaskStatus.COMPLETED,
                "기존 리포트입니다.",
                "기존 리포트 상세입니다.",
                "기존 권장 조치입니다."));
        taskArtifactRepository.save(TaskArtifact.create(
                created.taskId(),
                legacyReport.getId(),
                ArtifactType.FILE_PATH,
                "수정 파일",
                "src/main/java/back/domain/task/service/TaskService.java"));

        // when
        List<AgentReportResponse> responses = taskService.getTaskReports(workspaceId, created.taskId());

        // then
        assertThat(responses).hasSize(1);
        AgentReportResponse response = responses.getFirst();
        assertThat(response.reportId()).isEqualTo(legacyReport.getId());
        assertThat(response.summary()).isEqualTo("기존 리포트입니다.");
        assertThat(response.artifacts())
                .extracting(TaskArtifactResponse::artifactType, TaskArtifactResponse::name, TaskArtifactResponse::url)
                .containsExactly(tuple(
                        ArtifactType.FILE_PATH,
                        "수정 파일",
                        "src/main/java/back/domain/task/service/TaskService.java"));
    }

    @Test
    @DisplayName("Task 메시지 조회는 실행 응답 메시지와 산출물을 반환한다")
    void getTaskMessagesWithExecutionResponse() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, createRequest());
        TaskExecution execution = taskExecutionRepository.save(TaskExecution.queued(
                workspaceId,
                created.taskId(),
                1L,
                "openclaw-agent-1",
                3L,
                "feature/pr-review"));
        executionTaskArtifactRepository.save(ExecutionTaskArtifact.create(
                execution.getId(),
                new TaskArtifactSaveRequest(
                        "PR_URL",
                        "생성된 PR",
                        "https://github.com/example/repo/pull/1")));
        TaskMessage message = taskMessageRepository.save(TaskMessage.assistantResponse(
                workspaceId,
                created.taskId(),
                execution.getId(),
                "COMPLETED",
                "PR 리뷰가 완료되었습니다.\n\n산출물\n- [PR_URL] 생성된 PR",
                "PR 리뷰가 완료되었습니다.",
                "입력값 검증 개선 포인트를 확인했습니다.",
                "DTO validation 추가를 권장합니다."));

        // when
        List<TaskMessageResponse> responses = taskService.getTaskMessages(workspaceId, created.taskId());

        // then
        assertThat(responses).hasSize(1);
        TaskMessageResponse response = responses.getFirst();
        assertThat(response.messageId()).isEqualTo(message.getId());
        assertThat(response.taskId()).isEqualTo(created.taskId());
        assertThat(response.taskExecutionId()).isEqualTo(execution.getId());
        assertThat(response.role()).isEqualTo(TaskMessageRole.ASSISTANT);
        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.content()).contains("PR 리뷰가 완료되었습니다.", "산출물");
        assertThat(response.artifacts())
                .extracting(TaskArtifactResponse::artifactType, TaskArtifactResponse::name, TaskArtifactResponse::url)
                .containsExactly(tuple(
                        ArtifactType.PR_URL,
                        "생성된 PR",
                        "https://github.com/example/repo/pull/1"));
    }

    @Test
    @DisplayName("Task를 생성하고 Runner에 전달해 실행할 수 있다")
    void createAndRunTask() {
        // given
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willAnswer(invocation -> {
            TaskExecutionRunCommand command = invocation.getArgument(0);
            return new TaskExecutionRunResult(
                    20L,
                    command.taskId(),
                    command.workspaceId(),
                    100L,
                    TaskExecutionStatus.SUCCEEDED,
                    "/tmp/aioffice/workspaces/1/executions/20/repo",
                    "workspace-1-execution-20",
                    "작업을 완료했습니다.",
                    null);
        });

        // when
        TaskRunResponse response = taskRunService.createAndRunTask(workspaceId, createRunRequest());

        // then
        assertThat(response.taskId()).isNotNull();
        assertThat(response.taskStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.taskExecutionId()).isEqualTo(20L);
        assertThat(response.executionStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        assertThat(response.finalText()).isEqualTo("작업을 완료했습니다.");

        TaskDetailResponse detail = taskService.getTask(workspaceId, response.taskId());
        assertThat(detail.status()).isEqualTo(TaskStatus.COMPLETED);

        ArgumentCaptor<TaskExecutionRunCommand> commandCaptor =
                ArgumentCaptor.forClass(TaskExecutionRunCommand.class);
        verify(taskExecutionRunner).run(commandCaptor.capture());

        TaskExecutionRunCommand command = commandCaptor.getValue();
        assertThat(command.workspaceId()).isEqualTo(workspaceId);
        assertThat(command.taskId()).isEqualTo(response.taskId());
        assertThat(command.assignedAgentId()).isEqualTo(1L);
        assertThat(command.repositoryId()).isEqualTo(3L);
        assertThat(command.prompt()).isEqualTo("이 PR 리뷰해줘");
        assertThat(command.createPr()).isTrue();
        assertThat(command.openClawSessionKeyOverride()).isNull();

        List<TaskMessageResponse> messages = taskService.getTaskMessages(workspaceId, response.taskId());
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().role()).isEqualTo(TaskMessageRole.USER);
        assertThat(messages.getFirst().content()).isEqualTo("이 PR 리뷰해줘");
    }

    @Test
    @DisplayName("Slack 출처 Task 실행이 완료되면 Slack reply 이벤트를 발행한다")
    void createAndRunSlackTaskPublishesSlackReplyEvent() {
        // given
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willAnswer(invocation -> {
            TaskExecutionRunCommand command = invocation.getArgument(0);
            return new TaskExecutionRunResult(
                    20L,
                    command.taskId(),
                    command.workspaceId(),
                    100L,
                    TaskExecutionStatus.SUCCEEDED,
                    "/tmp/aioffice/workspaces/1/executions/20/repo",
                    "workspace-1-execution-20",
                    "작업을 완료했습니다.",
                    null);
        });

        // when
        TaskRunResponse response = taskRunService.createAndRunTask(workspaceId, createSlackRunRequest());

        // then
        assertThat(response.taskStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(applicationEvents.stream(SlackReplyRequestedEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.sourceRef()).isEqualTo("T123:C456:1715059800.123");
                    assertThat(event.message()).isEqualTo("작업을 완료했습니다.");
                    assertThat(event.deduplicationKey())
                            .isEqualTo("slack-task-" + response.taskId() + "-execution-20");
                });
    }

    @Test
    @DisplayName("Slack 출처 Task 실행 중 예외가 발생하면 실패 Slack reply 이벤트를 발행한다")
    void createAndRunSlackTaskWithRunnerExceptionPublishesFailureReplyEvent() {
        // given
        RuntimeException runnerException = new IllegalStateException("runner failed");
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willThrow(runnerException);

        // when & then
        assertThatThrownBy(() -> taskRunService.createAndRunTask(workspaceId, createSlackRunRequest()))
                .isSameAs(runnerException);
        assertThat(applicationEvents.stream(SlackReplyRequestedEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.sourceRef()).isEqualTo("T123:C456:1715059800.123");
                    assertThat(event.message()).contains("Task 실행에 실패했습니다.", "runner failed");
                    assertThat(event.deduplicationKey()).startsWith("slack-task-").endsWith("-failed");
                });
    }

    @Test
    @DisplayName("Task 실행 시 OpenClaw sessionKey override를 Runner에 전달한다")
    void runTaskWithOpenClawSessionKeyOverride() {
        // given
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willAnswer(invocation -> {
            TaskExecutionRunCommand command = invocation.getArgument(0);
            return new TaskExecutionRunResult(
                    20L,
                    command.taskId(),
                    command.workspaceId(),
                    100L,
                    TaskExecutionStatus.SUCCEEDED,
                    "/tmp/aioffice/workspaces/1/executions/20/repo",
                    command.openClawSessionKeyOverride(),
                    "작업을 완료했습니다.",
                    null);
        });
        TaskRunResponse accepted = taskRunService.createTaskForRun(workspaceId, createRunRequest());

        // when
        TaskRunResponse response =
                taskRunService.runTask(workspaceId, accepted.taskId(), true, "workspace-1-agent-1-chat-fixed");

        // then
        assertThat(response.taskExecutionId()).isEqualTo(20L);

        ArgumentCaptor<TaskExecutionRunCommand> commandCaptor =
                ArgumentCaptor.forClass(TaskExecutionRunCommand.class);
        verify(taskExecutionRunner).run(commandCaptor.capture());
        assertThat(commandCaptor.getValue().openClawSessionKeyOverride())
                .isEqualTo("workspace-1-agent-1-chat-fixed");
    }

    @Test
    @DisplayName("Runner 실행 중 Task가 종료 상태로 바뀌면 최종 상태를 덮어쓰지 않는다")
    void createAndRunTaskWithTerminalTaskStatus() {
        // given
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willAnswer(invocation -> {
            TaskExecutionRunCommand command = invocation.getArgument(0);
            taskService.updateStatus(
                    command.workspaceId(),
                    command.taskId(),
                    new TaskStatusUpdateRequest(TaskStatus.CANCELED, "사용자 취소"));
            return new TaskExecutionRunResult(
                    20L,
                    command.taskId(),
                    command.workspaceId(),
                    100L,
                    TaskExecutionStatus.SUCCEEDED,
                    "/tmp/aioffice/workspaces/1/executions/20/repo",
                    "workspace-1-execution-20",
                    "작업을 완료했습니다.",
                    null);
        });

        // when
        TaskRunResponse response = taskRunService.createAndRunTask(workspaceId, createRunRequest());

        // then
        assertThat(response.taskStatus()).isEqualTo(TaskStatus.CANCELED);

        TaskDetailResponse detail = taskService.getTask(workspaceId, response.taskId());
        assertThat(detail.status()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    @DisplayName("Runner 예외 발생 시 원래 예외를 유지하고 Task를 FAILED로 변경한다")
    void createAndRunTaskWithRunnerException() {
        // given
        RuntimeException runnerException = new IllegalStateException("runner failed");
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willThrow(runnerException);

        // when & then
        assertThatThrownBy(() -> taskRunService.createAndRunTask(workspaceId, createRunRequest()))
                .isSameAs(runnerException);

        Page<TaskListResponse> responses = taskService.getTasks(workspaceId, PageRequest.of(0, 10));
        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).status()).isEqualTo(TaskStatus.FAILED);
    }

    private TaskCreateRequest createRequest() {
        return new TaskCreateRequest(
                "PR 리뷰",
                "최근 PR 변경사항을 리뷰한다.",
                TaskType.PR_REVIEW,
                TaskPriority.HIGH,
                1L,
                3L,
                SourceType.DASHBOARD,
                "dashboard-test",
                "이 PR 리뷰해줘");
    }

    private TaskRunRequest createRunRequest() {
        return new TaskRunRequest(
                "PR 리뷰",
                "최근 PR 변경사항을 리뷰한다.",
                TaskType.PR_REVIEW,
                TaskPriority.HIGH,
                1L,
                3L,
                SourceType.DASHBOARD,
                "dashboard-test",
                "이 PR 리뷰해줘",
                true);
    }

    private TaskRunRequest createSlackRunRequest() {
        return new TaskRunRequest(
                "Slack 요청 작업",
                "Slack thread에서 요청한 작업입니다.",
                TaskType.FEATURE_IMPLEMENTATION,
                TaskPriority.MEDIUM,
                1L,
                3L,
                SourceType.SLACK,
                "T123:C456:1715059800.123",
                "로그인 API 구현해줘",
                false);
    }
}
