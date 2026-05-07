package back.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import back.domain.execution.entity.TaskExecution;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.entity.LogLevel;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskExecutionLog;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class TaskExecutionLogServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskExecutionService taskExecutionService;

    @Autowired
    private TaskExecutionLogService taskExecutionLogService;

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
                "test-google-sub-" + UUID.randomUUID(),
                "test-" + UUID.randomUUID() + "@test.com",
                "테스트 멤버"
        ));

        Workspace workspace = workspaceRepository.save(Workspace.create(
                "테스트 워크스페이스",
                "테스트용 워크스페이스입니다.",
                member
        ));

        workspaceMemberRepository.save(
                WorkspaceMember.create(workspace, member, WorkspaceMemberRole.ADMIN)
        );

        workspaceId = workspace.getId();
        memberId = member.getId();
    }

    @Test
    @DisplayName("Task 실행 로그를 저장할 수 있다")
    void saveLog() {
        // given
        TaskExecution execution = createRunningExecution();

        // when
        TaskExecutionLog log = taskExecutionLogService.saveLog(
                execution.getId(),
                LogLevel.INFO,
                "코드 분석 시작"
        );

        // then
        assertThat(log.getId()).isNotNull();
        assertThat(log.getExecutionId()).isEqualTo(execution.getId());
        assertThat(log.getLevel()).isEqualTo(LogLevel.INFO);
        assertThat(log.getMessage()).isEqualTo("코드 분석 시작");
    }

    @Test
    @DisplayName("executionId 기준으로 실행 로그를 조회할 수 있다")
    void getLogsByExecution() {
        // given
        TaskExecution execution = createRunningExecution();

        taskExecutionLogService.saveLog(
                execution.getId(),
                LogLevel.INFO,
                "repository clone 시작"
        );
        taskExecutionLogService.saveLog(
                execution.getId(),
                LogLevel.WARN,
                "변경 파일 수가 많습니다"
        );

        // when
        List<TaskLogResponse> logs =
                taskExecutionLogService.getLogsByExecution(execution.getId());

        // then
        assertThat(logs).hasSize(2);
        assertThat(logs)
                .extracting(TaskLogResponse::message)
                .contains("repository clone 시작", "변경 파일 수가 많습니다");
        assertThat(logs)
                .extracting(TaskLogResponse::level)
                .contains(LogLevel.INFO, LogLevel.WARN);
    }

    @Test
    @DisplayName("Task의 최신 실행 로그를 조회할 수 있다")
    void getLatestTaskLogs() {
        // given
        TaskCreateResponse task = createTask();
        TaskExecution execution = taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );

        taskExecutionLogService.saveLog(
                execution.getId(),
                LogLevel.INFO,
                "작업 시작"
        );
        taskExecutionLogService.saveLog(
                execution.getId(),
                LogLevel.INFO,
                "코드 분석 완료"
        );

        // when
        List<TaskLogResponse> logs =
                taskExecutionLogService.getLatestTaskLogs(workspaceId, task.taskId());

        // then
        assertThat(logs).hasSize(2);
        assertThat(logs)
                .extracting(TaskLogResponse::executionId)
                .containsOnly(execution.getId());
        assertThat(logs)
                .extracting(TaskLogResponse::message)
                .contains("작업 시작", "코드 분석 완료");
    }

    private TaskExecution createRunningExecution() {
        TaskCreateResponse task = createTask();

        return taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );
    }

    private TaskCreateResponse createTask() {
        return taskService.createTask(
                workspaceId,
                memberId,
                new TaskCreateRequest(
                        "PR 리뷰",
                        "최근 PR 변경사항을 리뷰한다.",
                        TaskType.PR_REVIEW,
                        TaskPriority.HIGH,
                        1L,
                        3L,
                        SourceType.DASHBOARD,
                        "dashboard-test",
                        "이 PR 리뷰해줘"
                )
        );
    }
}