package back.domain.task.service;

import org.springframework.stereotype.Service;

import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.service.TaskExecutionRunner;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskStatus;
import back.domain.task.repository.TaskRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskRunService {

    private final TaskRepository taskRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TaskExecutionRunner taskExecutionRunner;

    public TaskRunResponse createAndRunTask(Long workspaceId, TaskRunRequest request) {
        validateWorkspaceExists(workspaceId);
        Task task = createInProgressTask(workspaceId, request);

        TaskExecutionRunResult executionResult;
        try {
            executionResult = taskExecutionRunner.run(new TaskExecutionRunCommand(
                    task.getWorkspaceId(),
                    task.getId(),
                    task.getRepositoryId(),
                    resolveExecutionPrompt(task),
                    request.shouldCreatePr()));
        } catch (RuntimeException exception) {
            markTaskFailedBestEffort(task.getWorkspaceId(), task.getId(), exception);
            throw exception;
        }

        Task finishedTask =
                markTaskStatus(task.getWorkspaceId(), task.getId(), resolveTaskStatus(executionResult.status()));
        return TaskRunResponse.from(finishedTask, executionResult);
    }

    private Task createInProgressTask(Long workspaceId, TaskRunRequest request) {
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
                request.originalRequest());
        task.updateStatus(TaskStatus.IN_PROGRESS);
        return taskRepository.save(task);
    }

    private Task markTaskStatus(Long workspaceId, Long taskId, TaskStatus taskStatus) {
        Task task = getTaskOrThrow(workspaceId, taskId);
        if (isTerminalStatus(task.getStatus()) && task.getStatus() != taskStatus) {
            return task;
        }
        task.updateStatus(taskStatus);
        return taskRepository.save(task);
    }

    private void markTaskFailedBestEffort(Long workspaceId, Long taskId, RuntimeException originalException) {
        try {
            markTaskStatus(workspaceId, taskId, TaskStatus.FAILED);
        } catch (RuntimeException statusException) {
            log.warn(
                    "Failed to mark task as FAILED after runner exception. workspaceId={}, taskId={}",
                    workspaceId,
                    taskId,
                    statusException);
            originalException.addSuppressed(statusException);
        }
    }

    private Task getTaskOrThrow(Long workspaceId, Long taskId) {
        return taskRepository
                .findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "Task를 찾을 수 없습니다. taskId=" + taskId + ", workspaceId=" + workspaceId,
                        "Task를 찾을 수 없습니다."));
    }

    private void validateWorkspaceExists(Long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "Workspace를 찾을 수 없습니다. workspaceId=" + workspaceId,
                    "Workspace를 찾을 수 없습니다.");
        }
    }

    private TaskStatus resolveTaskStatus(TaskExecutionStatus executionStatus) {
        return switch (executionStatus) {
            case SUCCEEDED -> TaskStatus.COMPLETED;
            case FAILED -> TaskStatus.FAILED;
            case CANCELED -> TaskStatus.CANCELED;
            case QUEUED, RUNNING -> TaskStatus.IN_PROGRESS;
        };
    }

    private boolean isTerminalStatus(TaskStatus taskStatus) {
        return taskStatus == TaskStatus.COMPLETED
                || taskStatus == TaskStatus.FAILED
                || taskStatus == TaskStatus.CANCELED;
    }

    private String resolveExecutionPrompt(Task task) {
        if (hasText(task.getOriginalRequest())) {
            return task.getOriginalRequest();
        }
        if (hasText(task.getDescription())) {
            return task.getDescription();
        }
        return task.getTitle();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
