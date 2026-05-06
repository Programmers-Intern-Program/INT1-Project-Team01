package back.domain.task.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(),
                "test-" + UUID.randomUUID() + "@test.com",
                "테스트 멤버"
        ));

        Workspace workspace = workspaceRepository.save(Workspace.create(
                "테스트 워크스페이스",
                "테스트용 워크스페이스입니다.",
                member
        ));

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
        Page<TaskListResponse> responses =
                taskService.getTasks(workspaceId, PageRequest.of(0, 10));

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

        TaskStatusUpdateRequest request = new TaskStatusUpdateRequest(
                TaskStatus.IN_PROGRESS,
                "Agent 작업 시작"
        );

        // when
        TaskStatusUpdateResponse response =
                taskService.updateStatus(workspaceId, created.taskId(), request);

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
                "이 PR 리뷰해줘"
        );
    }
}