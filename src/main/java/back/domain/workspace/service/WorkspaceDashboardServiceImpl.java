package back.domain.workspace.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.repository.ExecutionAgentReportRepository;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.task.entity.TaskExecutionLog;
import back.domain.task.entity.TaskStatus;
import back.domain.task.repository.TaskExecutionLogRepository;
import back.domain.task.repository.TaskRepository;
import back.domain.workspace.dto.response.DashboardRecentLogRes;
import back.domain.workspace.dto.response.DashboardRecentReportRes;
import back.domain.workspace.dto.response.WorkspaceDashboardSummaryRes;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceDashboardServiceImpl implements WorkspaceDashboardService {
    private static final int RECENT_EXECUTION_LOOKUP_LIMIT = 20;
    private static final int RECENT_ITEM_LIMIT = 5;

    private final WorkspaceAccessValidator workspaceAccessValidator;
    private final AgentRepository agentRepository;
    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final ExecutionAgentReportRepository executionAgentReportRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;

    @Override
    public WorkspaceDashboardSummaryRes getSummary(long workspaceId, long memberId) {
        workspaceAccessValidator.requireMember(workspaceId, memberId);

        long agentCount = agentRepository.countByWorkspaceIdAndStatusNot(workspaceId, AgentStatus.DISABLED);
        long runningAgentCount =
                taskExecutionRepository.countRunningAgentCount(workspaceId, TaskExecutionStatus.RUNNING);
        long readyAgentCount = agentRepository.countByWorkspaceIdAndStatus(workspaceId, AgentStatus.READY);
        long idleAgentCount = Math.max(0, readyAgentCount - runningAgentCount);
        long errorAgentCount = agentRepository.countByWorkspaceIdAndStatusIn(
                workspaceId,
                List.of(AgentStatus.ERROR, AgentStatus.SYNC_FAILED));

        long taskCount = taskRepository.countByWorkspaceId(workspaceId);
        long runningTaskCount = taskRepository.countByWorkspaceIdAndStatusIn(
                workspaceId, List.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS, TaskStatus.WAITING_USER));
        long completedTaskCount = taskRepository.countByWorkspaceIdAndStatus(workspaceId, TaskStatus.COMPLETED);
        long failedTaskCount = taskRepository.countByWorkspaceIdAndStatus(workspaceId, TaskStatus.FAILED);

        List<TaskExecution> recentExecutions = getRecentExecutions(workspaceId);

        return new WorkspaceDashboardSummaryRes(
                agentCount,
                runningAgentCount,
                idleAgentCount,
                errorAgentCount,
                taskCount,
                runningTaskCount,
                completedTaskCount,
                failedTaskCount,
                getRecentReports(recentExecutions),
                getRecentLogs(recentExecutions));
    }

    private List<TaskExecution> getRecentExecutions(long workspaceId) {
        return taskExecutionRepository.findByWorkspaceIdOrderByCreatedAtDesc(
                workspaceId, PageRequest.of(0, RECENT_EXECUTION_LOOKUP_LIMIT));
    }

    private List<DashboardRecentReportRes> getRecentReports(List<TaskExecution> recentExecutions) {
        Map<Long, TaskExecution> executionsById = toExecutionMap(recentExecutions);
        if (executionsById.isEmpty()) {
            return List.of();
        }

        return executionAgentReportRepository
                .findAllByTaskExecutionIdInOrderByCreatedAtDescIdDesc(executionsById.keySet())
                .stream()
                .limit(RECENT_ITEM_LIMIT)
                .map(report -> toRecentReportResponse(report, executionsById.get(report.getTaskExecutionId())))
                .toList();
    }

    private List<DashboardRecentLogRes> getRecentLogs(List<TaskExecution> recentExecutions) {
        Map<Long, TaskExecution> executionsById = toExecutionMap(recentExecutions);
        if (executionsById.isEmpty()) {
            return List.of();
        }

        return taskExecutionLogRepository
                .findTop5ByExecutionIdInOrderByCreatedAtDescIdDesc(executionsById.keySet())
                .stream()
                .map(log -> toRecentLogResponse(log, executionsById.get(log.getExecutionId())))
                .toList();
    }

    private Map<Long, TaskExecution> toExecutionMap(Collection<TaskExecution> executions) {
        return executions.stream().collect(Collectors.toMap(TaskExecution::getId, Function.identity()));
    }

    private DashboardRecentReportRes toRecentReportResponse(
            ExecutionAgentReport report,
            TaskExecution execution) {
        return new DashboardRecentReportRes(
                report.getId(),
                report.getTaskExecutionId(),
                execution.getTaskId(),
                execution.getAgentId(),
                report.getStatus(),
                report.getSummary(),
                report.getCreatedAt());
    }

    private DashboardRecentLogRes toRecentLogResponse(TaskExecutionLog log, TaskExecution execution) {
        return new DashboardRecentLogRes(
                log.getId(),
                log.getExecutionId(),
                execution.getTaskId(),
                execution.getAgentId(),
                log.getLevel(),
                log.getMessage(),
                log.getCreatedAt());
    }
}
