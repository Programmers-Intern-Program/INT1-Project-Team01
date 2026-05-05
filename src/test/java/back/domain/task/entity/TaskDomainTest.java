package back.domain.task.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDomainTest {

    @Test
    @DisplayName("TaskExecution을 생성하면 PENDING 상태가 된다")
    void createTaskExecution() {
        // given
        Long taskId = 1L;

        // when
        TaskExecution execution = TaskExecution.create(taskId);

        // then
        assertThat(execution.getTaskId()).isEqualTo(taskId);
        assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.PENDING);
        assertThat(execution.getStartedAt()).isNull();
        assertThat(execution.getFinishedAt()).isNull();
        assertThat(execution.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("TaskExecution을 시작 상태로 변경할 수 있다")
    void startTaskExecution() {
        // given
        TaskExecution execution = TaskExecution.create(1L);

        // when
        execution.start();

        // then
        assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.RUNNING);
        assertThat(execution.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("TaskExecution을 성공 상태로 변경할 수 있다")
    void successTaskExecution() {
        // given
        TaskExecution execution = TaskExecution.create(1L);

        // when
        execution.success();

        // then
        assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(execution.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("TaskExecution을 실패 상태로 변경할 수 있다")
    void failTaskExecution() {
        // given
        TaskExecution execution = TaskExecution.create(1L);
        String failureReason = "Agent 실행 실패";

        // when
        execution.fail(failureReason);

        // then
        assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo(failureReason);
        assertThat(execution.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("TaskExecutionLog를 생성할 수 있다")
    void createTaskExecutionLog() {
        // given
        Long executionId = 1L;
        LogLevel level = LogLevel.INFO;
        String message = "코드 분석 시작";

        // when
        TaskExecutionLog log = TaskExecutionLog.create(executionId, level, message);

        // then
        assertThat(log.getExecutionId()).isEqualTo(executionId);
        assertThat(log.getLevel()).isEqualTo(LogLevel.INFO);
        assertThat(log.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("AgentReport를 생성할 수 있다")
    void createAgentReport() {
        // given
        Long taskId = 1L;
        Long executionId = 1L;

        // when
        AgentReport report = AgentReport.create(
                taskId,
                executionId,
                TaskStatus.COMPLETED,
                "PR 리뷰 완료",
                "예외 처리 보강이 필요합니다.",
                "테스트 코드를 추가하세요."
        );

        // then
        assertThat(report.getTaskId()).isEqualTo(taskId);
        assertThat(report.getExecutionId()).isEqualTo(executionId);
        assertThat(report.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(report.getSummary()).isEqualTo("PR 리뷰 완료");
        assertThat(report.getDetail()).isEqualTo("예외 처리 보강이 필요합니다.");
        assertThat(report.getRecommendedAction()).isEqualTo("테스트 코드를 추가하세요.");
    }

    @Test
    @DisplayName("TaskArtifact를 생성할 수 있다")
    void createTaskArtifact() {
        // given
        Long taskId = 1L;
        Long reportId = 1L;

        // when
        TaskArtifact artifact = TaskArtifact.create(
                taskId,
                reportId,
                ArtifactType.PR_URL,
                "PR 링크",
                "https://github.com/test/repo/pull/1"
        );

        // then
        assertThat(artifact.getTaskId()).isEqualTo(taskId);
        assertThat(artifact.getReportId()).isEqualTo(reportId);
        assertThat(artifact.getArtifactType()).isEqualTo(ArtifactType.PR_URL);
        assertThat(artifact.getName()).isEqualTo("PR 링크");
        assertThat(artifact.getUrl()).isEqualTo("https://github.com/test/repo/pull/1");
    }
}