package back.domain.task.service;

import back.domain.task.entity.AgentReport;
import back.domain.task.entity.ArtifactType;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskArtifact;
import back.domain.task.entity.TaskExecution;
import back.domain.task.entity.TaskExecutionStatus;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.task.dto.request.AgentReportSaveRequest;
import back.domain.task.dto.request.TaskArtifactSaveRequest;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.response.SlackReportMessageResponse;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ExecutionReportServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskExecutionService taskExecutionService;

    @Autowired
    private AgentReportService agentReportService;

    @Autowired
    private TaskArtifactService taskArtifactService;

    @Autowired
    private ReportMessageService reportMessageService;

    private final Long workspaceId = 1L;

    @Test
    @DisplayName("Task 실행을 시작하면 실행 상태와 Task 상태가 변경된다")
    void startExecution() {
        // given
        TaskCreateResponse task = createTask();

        // when
        TaskExecution execution = taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );

        TaskDetailResponse taskDetail = taskService.getTask(
                workspaceId,
                task.taskId()
        );

        // then
        assertThat(execution.getId()).isNotNull();
        assertThat(execution.getTaskId()).isEqualTo(task.taskId());
        assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.RUNNING);
        assertThat(execution.getStartedAt()).isNotNull();
        assertThat(taskDetail.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Task 실행을 성공 처리할 수 있다")
    void completeExecution() {
        // given
        TaskCreateResponse task = createTask();
        TaskExecution execution = taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );

        // when
        TaskExecution completed = taskExecutionService.completeExecution(
                workspaceId,
                task.taskId(),
                execution.getId()
        );

        TaskDetailResponse taskDetail = taskService.getTask(
                workspaceId,
                task.taskId()
        );

        // then
        assertThat(completed.getStatus()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(completed.getFinishedAt()).isNotNull();
        assertThat(taskDetail.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("Task 실행을 실패 처리할 수 있다")
    void failExecution() {
        // given
        TaskCreateResponse task = createTask();
        TaskExecution execution = taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );

        // when
        TaskExecution failed = taskExecutionService.failExecution(
                workspaceId,
                task.taskId(),
                execution.getId(),
                "Agent 실행 실패"
        );

        TaskDetailResponse taskDetail = taskService.getTask(
                workspaceId,
                task.taskId()
        );

        // then
        assertThat(failed.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Agent 실행 실패");
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(taskDetail.status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("Agent 결과 보고를 저장할 수 있다")
    void saveReport() {
        // given
        TaskCreateResponse task = createTask();
        TaskExecution execution = taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );

        AgentReportSaveRequest request = new AgentReportSaveRequest(
                TaskStatus.COMPLETED,
                "PR 리뷰 완료",
                "예외 처리 보강이 필요합니다.",
                "테스트 코드를 추가하세요."
        );

        // when
        AgentReport report = agentReportService.saveReport(
                task.taskId(),
                execution.getId(),
                request
        );

        // then
        assertThat(report.getId()).isNotNull();
        assertThat(report.getTaskId()).isEqualTo(task.taskId());
        assertThat(report.getExecutionId()).isEqualTo(execution.getId());
        assertThat(report.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(report.getSummary()).isEqualTo("PR 리뷰 완료");
        assertThat(report.getDetail()).isEqualTo("예외 처리 보강이 필요합니다.");
        assertThat(report.getRecommendedAction()).isEqualTo("테스트 코드를 추가하세요.");
    }

    @Test
    @DisplayName("Task 산출물을 1개 저장할 수 있다")
    void saveArtifact() {
        // given
        AgentReport report = createReport();

        TaskArtifactSaveRequest request = new TaskArtifactSaveRequest(
                ArtifactType.PR_URL,
                "PR 링크",
                "https://github.com/test/repo/pull/1"
        );

        // when
        TaskArtifact artifact = taskArtifactService.saveArtifact(
                report.getTaskId(),
                report.getId(),
                request
        );

        // then
        assertThat(artifact.getId()).isNotNull();
        assertThat(artifact.getTaskId()).isEqualTo(report.getTaskId());
        assertThat(artifact.getReportId()).isEqualTo(report.getId());
        assertThat(artifact.getArtifactType()).isEqualTo(ArtifactType.PR_URL);
        assertThat(artifact.getName()).isEqualTo("PR 링크");
        assertThat(artifact.getUrl()).isEqualTo("https://github.com/test/repo/pull/1");
    }

    @Test
    @DisplayName("Task 산출물을 여러 개 저장할 수 있다")
    void saveArtifacts() {
        // given
        AgentReport report = createReport();

        List<TaskArtifactSaveRequest> requests = List.of(
                new TaskArtifactSaveRequest(
                        ArtifactType.PR_URL,
                        "PR 링크",
                        "https://github.com/test/repo/pull/1"
                ),
                new TaskArtifactSaveRequest(
                        ArtifactType.COMMIT_HASH,
                        "커밋 해시",
                        "abc123"
                )
        );

        // when
        List<TaskArtifact> artifacts = taskArtifactService.saveArtifacts(
                report.getTaskId(),
                report.getId(),
                requests
        );

        // then
        assertThat(artifacts).hasSize(2);
        assertThat(artifacts)
                .extracting(TaskArtifact::getArtifactType)
                .contains(ArtifactType.PR_URL, ArtifactType.COMMIT_HASH);
    }

    @Test
    @DisplayName("Slack 보고 메시지를 생성할 수 있다")
    void createSlackReportMessage() {
        // given
        AgentReport report = createReport();

        taskArtifactService.saveArtifact(
                report.getTaskId(),
                report.getId(),
                new TaskArtifactSaveRequest(
                        ArtifactType.PR_URL,
                        "PR 링크",
                        "https://github.com/test/repo/pull/1"
                )
        );

        // when
        SlackReportMessageResponse response =
                reportMessageService.createSlackReportMessage(report.getTaskId());

        // then
        assertThat(response.taskId()).isEqualTo(report.getTaskId());
        assertThat(response.message()).contains("PR 리뷰 완료");
        assertThat(response.message()).contains("예외 처리 보강이 필요합니다.");
        assertThat(response.message()).contains("테스트 코드를 추가하세요.");
        assertThat(response.message()).contains("https://github.com/test/repo/pull/1");
    }

    private AgentReport createReport() {
        TaskCreateResponse task = createTask();
        TaskExecution execution = taskExecutionService.startExecution(
                workspaceId,
                task.taskId()
        );

        AgentReportSaveRequest request = new AgentReportSaveRequest(
                TaskStatus.COMPLETED,
                "PR 리뷰 완료",
                "예외 처리 보강이 필요합니다.",
                "테스트 코드를 추가하세요."
        );

        return agentReportService.saveReport(
                task.taskId(),
                execution.getId(),
                request
        );
    }

    private TaskCreateResponse createTask() {
        return taskService.createTask(
                workspaceId,
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