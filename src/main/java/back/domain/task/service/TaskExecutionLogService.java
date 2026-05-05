package back.domain.task.service;

import back.domain.task.entity.LogLevel;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskExecution;
import back.domain.task.entity.TaskExecutionLog;
import back.domain.task.dto.response.TaskLogResponse;
import back.domain.task.repository.TaskExecutionLogRepository;
import back.domain.task.repository.TaskExecutionRepository;
import back.domain.task.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskExecutionLogService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;

    @Transactional
    public TaskExecutionLog saveLog(
            Long executionId,
            LogLevel level,
            String message
    ) {
        TaskExecution execution = findExecution(executionId);

        TaskExecutionLog log = TaskExecutionLog.create(
                execution.getId(),
                level,
                message
        );

        return taskExecutionLogRepository.save(log);
    }

    public List<TaskLogResponse> getLogsByExecution(Long executionId) {
        findExecution(executionId);

        return taskExecutionLogRepository.findByExecutionIdOrderByCreatedAtAsc(executionId)
                .stream()
                .map(TaskLogResponse::from)
                .toList();
    }

    public List<TaskLogResponse> getLatestTaskLogs(
            Long workspaceId,
            Long taskId
    ) {
        findTask(workspaceId, taskId);

        TaskExecution latestExecution = taskExecutionRepository.findTopByTaskIdOrderByCreatedAtDesc(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task 실행 기록을 찾을 수 없습니다."));

        return taskExecutionLogRepository.findByExecutionIdOrderByCreatedAtAsc(latestExecution.getId())
                .stream()
                .map(TaskLogResponse::from)
                .toList();
    }

    private Task findTask(
            Long workspaceId,
            Long taskId
    ) {
        return taskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));
    }

    private TaskExecution findExecution(Long executionId) {
        return taskExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Task 실행 기록을 찾을 수 없습니다."));
    }
}