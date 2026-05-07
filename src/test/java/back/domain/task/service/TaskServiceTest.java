package back.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.dto.response.AgentReportResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.dto.response.TaskArtifactResponse;
import back.domain.task.entity.AgentReport;
import back.domain.task.entity.ArtifactType;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskArtifact;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.task.repository.AgentReportRepository;
import back.domain.task.repository.TaskArtifactRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
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
    private WorkspaceMemberRepository workspaceMemberRepository;

    private Long workspaceId;

    private long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(), "test-" + UUID.randomUUID() + "@test.com", "테스트 멤버"));

        Workspace workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "테스트용 워크스페이스입니다.", member));

        workspaceMemberRepository.save(
                WorkspaceMember.create(workspace, member, WorkspaceMemberRole.ADMIN)
        );

        workspaceId = workspace.getId();
        memberId = member.getId();
    }

    @Test
    @DisplayName("Task를 생성할 수 있다")
    void createTask() {
        // given
        TaskCreateRequest request = createRequest();

        // when
        TaskCreateResponse response = taskService.createTask(workspaceId, memberId, request);

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
        taskService.createTask(workspaceId, memberId, createRequest());

        // when
        Page<TaskListResponse> responses =
                taskService.getTasks(workspaceId, memberId, PageRequest.of(0, 10));

        // then
        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).title()).isEqualTo("PR 리뷰");
        assertThat(responses.getContent().get(0).status()).isEqualTo(TaskStatus.REQUESTED);
    }

    @Test
    @DisplayName("Task 상세 정보를 조회할 수 있다")
    void getTask() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, memberId, createRequest());

        // when
        TaskDetailResponse response = taskService.getTask(workspaceId, memberId, created.taskId());

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
        TaskCreateResponse created = taskService.createTask(workspaceId, memberId, createRequest());

        TaskStatusUpdateRequest request = new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS, "Agent 작업 시작");

        // when
        TaskStatusUpdateResponse response =
                taskService.updateStatus(workspaceId, memberId, created.taskId(), request);

        // then
        assertThat(response.taskId()).isEqualTo(created.taskId());
        assertThat(response.previousStatus()).isEqualTo(TaskStatus.REQUESTED);
        assertThat(response.currentStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Task는 존재하지만 실행 기록이 없으면 빈 로그 목록을 반환한다")
    void getTaskLogsWithoutExecution() {
        // given
        TaskCreateResponse created = taskService.createTask(workspaceId, memberId, createRequest());

        // when
        List<TaskLogResponse> responses = taskService.getTaskLogs(workspaceId, memberId, created.taskId());

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
        assertThat(command.repositoryId()).isEqualTo(3L);
        assertThat(command.prompt()).isEqualTo("이 PR 리뷰해줘");
        assertThat(command.createPr()).isTrue();
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
}
