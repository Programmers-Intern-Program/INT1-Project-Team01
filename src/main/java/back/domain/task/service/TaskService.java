package back.domain.task.service;

import back.domain.task.entity.Task;
import back.domain.task.entity.TaskStatus;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.repository.*;
import back.domain.task.entity.AgentReport;
import back.domain.task.entity.TaskExecution;
import back.domain.task.dto.response.AgentReportResponse;
import back.domain.task.dto.response.TaskArtifactResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.repository.AgentReportRepository;
import back.domain.task.repository.TaskArtifactRepository;
import back.domain.task.repository.TaskExecutionLogRepository;
import back.domain.task.repository.TaskExecutionRepository;

import java.util.List;

import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final AgentReportRepository agentReportRepository;
    private final TaskArtifactRepository taskArtifactRepository;

    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional
    public TaskCreateResponse createTask(Long workspaceId, long memberId, TaskCreateRequest request) {
        validateWorkspaceMember(workspaceId, memberId);

        Task task = Task.create(
                workspaceId,
                request.title(),
                request.description(),
                request.taskType(),
                request.priority(),
                request.assignedAgentId(),
                request.repositoryId(),
                request.sourceType(),
                request.sourceId(),
                request.originalRequest()
        );

        Task savedTask = taskRepository.save(task);

        return TaskCreateResponse.from(savedTask);
    }

    public Page<TaskListResponse> getTasks(Long workspaceId, long memberId, Pageable pageable) {
        validateWorkspaceMember(workspaceId, memberId);

        return taskRepository.findByWorkspaceId(workspaceId, pageable)
                .map(TaskListResponse::from);
    }

    public TaskDetailResponse getTask(Long workspaceId, long memberId, Long taskId) {
        validateWorkspaceMember(workspaceId, memberId);

        Task task = findTaskInWorkspace(taskId, workspaceId);

        return TaskDetailResponse.from(task);
    }

    @Transactional
    public TaskStatusUpdateResponse updateStatus(
            Long workspaceId,
            long memberId,
            Long taskId,
            TaskStatusUpdateRequest request
    ) {
        validateWorkspaceMember(workspaceId, memberId);

        Task task = findTaskInWorkspace(taskId, workspaceId);

        TaskStatus previousStatus = task.getStatus();

        task.updateStatus(request.status());
        taskRepository.flush();

        return new TaskStatusUpdateResponse(
                task.getId(),
                previousStatus,
                task.getStatus(),
                task.getUpdatedAt()
        );
    }

    public List<TaskLogResponse> getTaskLogs(Long workspaceId, long memberId, Long taskId) {
        validateWorkspaceMember(workspaceId, memberId);

        findTaskInWorkspace(taskId, workspaceId);

        TaskExecution latestExecution = taskExecutionRepository.findTopByTaskIdOrderByCreatedAtDesc(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task 실행 기록을 찾을 수 없습니다."));

        return taskExecutionLogRepository.findByExecutionIdOrderByCreatedAtAsc(latestExecution.getId())
                .stream()
                .map(TaskLogResponse::from)
                .toList();
    }

    public List<AgentReportResponse> getTaskReports(Long workspaceId, long memberId, Long taskId) {
        validateWorkspaceMember(workspaceId, memberId);

        findTaskInWorkspace(taskId, workspaceId);

        return agentReportRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(this::toAgentReportResponse)
                .toList();
    }

    private Task findTaskInWorkspace(Long taskId, Long workspaceId) {
        return taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));
    }

    private void validateWorkspaceMember(Long workspaceId, long memberId) {
        boolean exists = workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        );

        if (!exists) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[TaskService#validateWorkspaceMember] workspace access denied",
                    "워크스페이스 접근 권한이 없습니다."
            );
        }
    }

    private AgentReportResponse toAgentReportResponse(AgentReport report) {
        List<TaskArtifactResponse> artifacts = taskArtifactRepository.findByReportId(report.getId())
                .stream()
                .map(TaskArtifactResponse::from)
                .toList();

        return AgentReportResponse.of(report, artifacts);
    }
}