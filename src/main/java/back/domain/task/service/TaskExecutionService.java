package back.domain.task.service;

import back.domain.task.entity.Task;
import back.domain.task.entity.TaskExecution;
import back.domain.task.entity.TaskStatus;
import back.domain.task.repository.TaskExecutionRepository;
import back.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskExecutionService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;

    @Transactional
    public TaskExecution startExecution(Long workspaceId, Long taskId) {
        Task task = findTask(workspaceId, taskId);

        TaskExecution execution = TaskExecution.create(taskId);
        execution.start();

        task.updateStatus(TaskStatus.IN_PROGRESS);

        return taskExecutionRepository.save(execution);
    }

    @Transactional
    public TaskExecution completeExecution(
            Long workspaceId,
            Long taskId,
            Long executionId
    ) {
        Task task = findTask(workspaceId, taskId);
        TaskExecution execution = findExecution(executionId);

        validateExecutionBelongsToTask(execution, taskId);

        execution.success();
        task.updateStatus(TaskStatus.COMPLETED);

        return execution;
    }

    @Transactional
    public TaskExecution failExecution(
            Long workspaceId,
            Long taskId,
            Long executionId,
            String failureReason
    ) {
        Task task = findTask(workspaceId, taskId);
        TaskExecution execution = findExecution(executionId);

        validateExecutionBelongsToTask(execution, taskId);

        execution.fail(failureReason);
        task.updateStatus(TaskStatus.FAILED);

        return execution;
    }

    private Task findTask(Long workspaceId, Long taskId) {
        return taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));
    }

    private TaskExecution findExecution(Long executionId) {
        return taskExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Task 실행 기록을 찾을 수 없습니다."));
    }

    private void validateExecutionBelongsToTask(
            TaskExecution execution,
            Long taskId
    ) {
        if (!execution.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Task와 실행 기록이 일치하지 않습니다.");
        }
    }
}