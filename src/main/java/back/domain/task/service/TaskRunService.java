package back.domain.task.service;

import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.service.TaskExecutionRunner;
import back.domain.slack.event.SlackReplyRequestedEvent;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskMessage;
import back.domain.task.entity.TaskMessageRole;
import back.domain.task.entity.TaskStatus;
import back.domain.task.repository.TaskMessageRepository;
import back.domain.task.repository.TaskRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskRunService {

    private final TaskRepository taskRepository;
    private final TaskMessageRepository taskMessageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TaskExecutionRunner taskExecutionRunner;
    private final TransactionOperations transactionOperations;
    private final ApplicationEventPublisher eventPublisher;

    public TaskRunResponse createAndRunTask(Long workspaceId, TaskRunRequest request) {
        validateWorkspaceExists(workspaceId);
        Task task = createInProgressTaskInTransaction(workspaceId, request);
        return runTask(task, request.shouldCreatePr(), null);
    }

    public TaskRunResponse createTaskForRun(Long workspaceId, TaskRunRequest request) {
        validateWorkspaceExists(workspaceId);
        Task task = createInProgressTaskInTransaction(workspaceId, request);
        return TaskRunResponse.accepted(task);
    }

    public TaskRunResponse runTask(Long workspaceId, Long taskId, boolean createPr) {
        Task task = getTaskOrThrow(workspaceId, taskId);
        return runTask(task, createPr, null);
    }

    public TaskRunResponse runTask(
            Long workspaceId,
            Long taskId,
            boolean createPr,
            String openClawSessionKeyOverride) {
        Task task = getTaskOrThrow(workspaceId, taskId);
        return runTask(task, createPr, openClawSessionKeyOverride);
    }

    public void markTaskFailed(Long workspaceId, Long taskId) {
        markTaskStatus(workspaceId, taskId, TaskStatus.FAILED);
    }

    private TaskRunResponse runTask(Task task, boolean createPr, String openClawSessionKeyOverride) {
        TaskExecutionRunResult executionResult;
        try {
            executionResult = taskExecutionRunner.run(new TaskExecutionRunCommand(
                    task.getWorkspaceId(),
                    task.getId(),
                    task.getAssignedAgentId(),
                    task.getRepositoryId(),
                    resolveExecutionPrompt(task),
                    createPr,
                    openClawSessionKeyOverride));
        } catch (RuntimeException exception) {
            markTaskFailedBestEffort(task.getWorkspaceId(), task.getId(), exception);
            publishSlackTaskFailure(task, exception);
            throw exception;
        }

        Task finishedTask =
                markTaskStatus(task.getWorkspaceId(), task.getId(), resolveTaskStatus(executionResult.status()));
        TaskRunResponse response = TaskRunResponse.from(finishedTask, executionResult);
        publishSlackTaskResult(finishedTask, response);
        return response;
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
        Task savedTask = taskRepository.save(task);
        taskMessageRepository.save(TaskMessage.userRequest(
                savedTask.getWorkspaceId(),
                savedTask.getId(),
                resolveExecutionPrompt(savedTask)));
        return savedTask;
    }

    private Task createInProgressTaskInTransaction(Long workspaceId, TaskRunRequest request) {
        return requireTransactionResult(transactionOperations.execute(
                status -> createInProgressTask(workspaceId, request)));
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

    private void publishSlackTaskResult(Task task, TaskRunResponse response) {
        if (!isSlackTask(task)) {
            return;
        }
        eventPublisher.publishEvent(new SlackReplyRequestedEvent(
                task.getSourceId(),
                resolveSlackTaskResultMessage(response),
                resolveSlackTaskResultDeduplicationKey(task, response)));
    }

    private void publishSlackTaskFailure(Task task, RuntimeException exception) {
        if (!isSlackTask(task)) {
            return;
        }
        eventPublisher.publishEvent(new SlackReplyRequestedEvent(
                task.getSourceId(),
                resolveSlackTaskFailureMessage(exception),
                "slack-task-" + task.getId() + "-failed"));
    }

    private boolean isSlackTask(Task task) {
        return task.getSourceType() == SourceType.SLACK
                && task.getSourceId() != null
                && !task.getSourceId().isBlank();
    }

    private String resolveSlackTaskResultMessage(TaskRunResponse response) {
        if (response.taskExecutionId() == null) {
            return fallbackSlackTaskResultMessage(response);
        }
        return taskMessageRepository
                .findFirstByWorkspaceIdAndTaskIdAndTaskExecutionIdAndRoleOrderByCreatedAtDescIdDesc(
                        response.workspaceId(),
                        response.taskId(),
                        response.taskExecutionId(),
                        TaskMessageRole.ASSISTANT)
                .map(TaskMessage::getContent)
                .orElseGet(() -> fallbackSlackTaskResultMessage(response));
    }

    private String fallbackSlackTaskResultMessage(TaskRunResponse response) {
        if (hasText(response.failureReason())) {
            return "Task 실행에 실패했습니다.\n\n" + response.failureReason();
        }
        if (hasText(response.finalText())) {
            return response.finalText();
        }
        return switch (response.taskStatus()) {
            case COMPLETED -> "Task 실행이 완료되었습니다.";
            case FAILED -> "Task 실행에 실패했습니다.";
            case CANCELED -> "Task 실행이 취소되었습니다.";
            default -> "Task 실행 상태가 변경되었습니다. 현재 상태: " + response.taskStatus();
        };
    }

    private String resolveSlackTaskFailureMessage(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException && hasText(serviceException.getClientMessage())) {
            return "Task 실행에 실패했습니다.\n\n" + serviceException.getClientMessage();
        }
        return "Task 실행에 실패했습니다.";
    }

    private String resolveSlackTaskResultDeduplicationKey(Task task, TaskRunResponse response) {
        if (response.taskExecutionId() == null) {
            return null;
        }
        return "slack-task-" + task.getId() + "-execution-" + response.taskExecutionId();
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

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("Task 생성 트랜잭션 결과가 비어 있습니다.");
        }
        return result;
    }
}
