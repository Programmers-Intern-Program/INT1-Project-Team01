package back.domain.task.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import back.domain.execution.repository.TaskExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.dto.response.AgentReportResponse;
import back.domain.task.dto.response.TaskArtifactResponse;
import back.domain.task.dto.response.TaskCreateResponse;
import back.domain.task.dto.response.TaskDetailResponse;
import back.domain.task.dto.response.TaskListResponse;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.dto.response.TaskStatusUpdateResponse;
import back.domain.task.entity.AgentReport;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskArtifact;
import back.domain.task.entity.TaskStatus;
import back.domain.task.repository.AgentReportRepository;
import back.domain.task.repository.TaskArtifactRepository;
import back.domain.task.repository.TaskExecutionLogRepository;

import back.domain.task.repository.TaskRepository;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final AgentReportRepository agentReportRepository;
    private final TaskArtifactRepository taskArtifactRepository;
    private final WorkspaceRepository workspaceRepository;
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

        Task task = getTaskOrThrow(workspaceId, taskId);

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

        Task task = getTaskOrThrow(workspaceId, taskId);
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
        getTaskOrThrow(workspaceId, taskId);

        return taskExecutionRepository.findTopByTaskIdOrderByCreatedAtDesc(taskId)
                .map(latestExecution -> taskExecutionLogRepository
                        .findByExecutionIdOrderByCreatedAtAsc(latestExecution.getId())
                        .stream()
                        .map(TaskLogResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    public List<AgentReportResponse> getTaskReports(Long workspaceId, long memberId, Long taskId) {
        validateWorkspaceMember(workspaceId, memberId);
        getTaskOrThrow(workspaceId, taskId);

        List<AgentReport> reports = agentReportRepository.findByTaskIdOrderByCreatedAtDesc(taskId);

        if (reports.isEmpty()) {
            return List.of();
        }

        List<Long> reportIds = reports.stream()
                .map(AgentReport::getId)
                .toList();

        Map<Long, List<TaskArtifactResponse>> artifactsByReportId = taskArtifactRepository.findByReportIdIn(reportIds)
                .stream()
                .collect(Collectors.groupingBy(
                        TaskArtifact::getReportId,
                        Collectors.mapping(TaskArtifactResponse::from, Collectors.toList())
                ));

        return reports.stream()
                .map(report -> AgentReportResponse.of(
                        report,
                        artifactsByReportId.getOrDefault(report.getId(), List.of())
                ))
                .toList();
    }

    private Task getTaskOrThrow(Long workspaceId, Long taskId) {
        return taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "Task를 찾을 수 없습니다. taskId=" + taskId + ", workspaceId=" + workspaceId,
                        "Task를 찾을 수 없습니다."
                ));
    }

    private void validateWorkspaceMember(Long workspaceId, long memberId) {
        validateWorkspaceExists(workspaceId);

        boolean exists = workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        );

        if (!exists) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[TaskService#validateWorkspaceMember] workspace access denied. "
                            + "workspaceId=" + workspaceId + ", memberId=" + memberId,
                    "워크스페이스 접근 권한이 없습니다."
            );
        }
    }

    private void validateWorkspaceExists(Long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "Workspace를 찾을 수 없습니다. workspaceId=" + workspaceId,
                    "Workspace를 찾을 수 없습니다."
            );
        }
    }
}