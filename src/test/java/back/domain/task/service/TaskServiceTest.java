package back.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.transaction.annotation.Transactional;

import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.TaskExecutionStatus;
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
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.workspace.entity.Workspace;
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
