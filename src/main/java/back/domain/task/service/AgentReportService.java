package back.domain.task.service;

import back.domain.execution.entity.TaskExecution;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.task.entity.AgentReport;
import back.domain.task.entity.Task;
import back.domain.task.dto.request.AgentReportSaveRequest;
import back.domain.task.repository.AgentReportRepository;

import back.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentReportService {

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final AgentReportRepository agentReportRepository;

    @Transactional
    public AgentReport saveReport(
            Long taskId,
            Long executionId,
            AgentReportSaveRequest request
    ) {
        Task task = findTask(taskId);
        TaskExecution execution = findExecution(executionId);

        validateExecutionBelongsToTask(execution, taskId);

        AgentReport report = AgentReport.create(
                taskId,
                executionId,
                request.status(),
                request.summary(),
                request.detail(),
                request.recommendedAction()
        );

        task.updateStatus(request.status());

        return agentReportRepository.save(report);
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
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