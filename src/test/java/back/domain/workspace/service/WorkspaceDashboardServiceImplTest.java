package back.domain.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.repository.ExecutionAgentReportRepository;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.task.entity.LogLevel;
import back.domain.task.entity.TaskExecutionLog;
import back.domain.task.entity.TaskStatus;
import back.domain.task.repository.TaskExecutionLogRepository;
import back.domain.task.repository.TaskRepository;
import back.domain.workspace.dto.response.WorkspaceDashboardSummaryRes;

@ExtendWith(MockitoExtension.class)
class WorkspaceDashboardServiceImplTest {

    @Mock private WorkspaceAccessValidator workspaceAccessValidator;
    @Mock private AgentRepository agentRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskExecutionRepository taskExecutionRepository;
    @Mock private ExecutionAgentReportRepository executionAgentReportRepository;
    @Mock private TaskExecutionLogRepository taskExecutionLogRepository;

    @Test
    @DisplayName("워크스페이스 대시보드 요약을 집계한다")
    void getSummary_success() {
        // given
        WorkspaceDashboardServiceImpl service = new WorkspaceDashboardServiceImpl(
                workspaceAccessValidator,
                agentRepository,
                taskRepository,
                taskExecutionRepository,
                executionAgentReportRepository,
                taskExecutionLogRepository);
        TaskExecution execution = TaskExecution.queued(1L, 10L, 20L, "openclaw-agent-1", null, null);
        ReflectionTestUtils.setField(execution, "id", 100L);
        ExecutionAgentReport report = ExecutionAgentReport.create(
                100L,
                new AgentReportSaveRequest("COMPLETED", "작업 완료", "상세", "없음"));
        ReflectionTestUtils.setField(report, "id", 200L);
        TaskExecutionLog log = TaskExecutionLog.create(100L, LogLevel.INFO, "실행 로그");
        ReflectionTestUtils.setField(log, "id", 300L);

        given(agentRepository.countByWorkspaceIdAndStatusNot(1L, AgentStatus.DISABLED)).willReturn(4L);
        given(taskExecutionRepository.countRunningAgentCount(1L, TaskExecutionStatus.RUNNING)).willReturn(1L);
        given(agentRepository.countByWorkspaceIdAndStatus(1L, AgentStatus.READY)).willReturn(3L);
        given(agentRepository.countByWorkspaceIdAndStatusIn(eq(1L), any())).willReturn(1L);
        given(taskRepository.countByWorkspaceId(1L)).willReturn(10L);
        given(taskRepository.countByWorkspaceIdAndStatusIn(eq(1L), any())).willReturn(3L);
        given(taskRepository.countByWorkspaceIdAndStatus(1L, TaskStatus.COMPLETED)).willReturn(6L);
        given(taskRepository.countByWorkspaceIdAndStatus(1L, TaskStatus.FAILED)).willReturn(1L);
        given(taskExecutionRepository.findByWorkspaceIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .willReturn(List.of(execution));
        given(executionAgentReportRepository.findAllByTaskExecutionIdInOrderByCreatedAtDescIdDesc(anyCollection()))
                .willReturn(List.of(report));
        given(taskExecutionLogRepository.findTop5ByExecutionIdInOrderByCreatedAtDescIdDesc(anyCollection()))
                .willReturn(List.of(log));

        // when
        WorkspaceDashboardSummaryRes result = service.getSummary(1L, 1L);

        // then
        verify(workspaceAccessValidator).requireMember(1L, 1L);
        assertThat(result.agentCount()).isEqualTo(4L);
        assertThat(result.runningAgentCount()).isEqualTo(1L);
        assertThat(result.idleAgentCount()).isEqualTo(2L);
        assertThat(result.errorAgentCount()).isEqualTo(1L);
        assertThat(result.taskCount()).isEqualTo(10L);
        assertThat(result.runningTaskCount()).isEqualTo(3L);
        assertThat(result.completedTaskCount()).isEqualTo(6L);
        assertThat(result.failedTaskCount()).isEqualTo(1L);
        assertThat(result.recentReports()).hasSize(1);
        assertThat(result.recentReports().getFirst().taskId()).isEqualTo(10L);
        assertThat(result.recentReports().getFirst().summary()).isEqualTo("작업 완료");
        assertThat(result.recentLogs()).hasSize(1);
        assertThat(result.recentLogs().getFirst().message()).isEqualTo("실행 로그");
    }
}
