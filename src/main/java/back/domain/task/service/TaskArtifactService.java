package back.domain.task.service;

import back.domain.task.entity.AgentReport;
import back.domain.task.entity.Task;
import back.domain.task.entity.TaskArtifact;
import back.domain.task.dto.request.TaskArtifactSaveRequest;
import back.domain.task.repository.AgentReportRepository;
import back.domain.task.repository.TaskArtifactRepository;
import back.domain.task.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskArtifactService {

    private final TaskRepository taskRepository;
    private final AgentReportRepository agentReportRepository;
    private final TaskArtifactRepository taskArtifactRepository;

    @Transactional
    public TaskArtifact saveArtifact(
            Long taskId,
            Long reportId,
            TaskArtifactSaveRequest request
    ) {
        findTask(taskId);
        AgentReport report = findReport(reportId);

        validateReportBelongsToTask(report, taskId);

        TaskArtifact artifact = TaskArtifact.create(
                taskId,
                reportId,
                request.artifactType(),
                request.name(),
                request.url()
        );

        return taskArtifactRepository.save(artifact);
    }

    @Transactional
    public List<TaskArtifact> saveArtifacts(
            Long taskId,
            Long reportId,
            List<TaskArtifactSaveRequest> requests
    ) {
        findTask(taskId);
        AgentReport report = findReport(reportId);

        validateReportBelongsToTask(report, taskId);

        List<TaskArtifact> artifacts = requests.stream()
                .map(request -> TaskArtifact.create(
                        taskId,
                        reportId,
                        request.artifactType(),
                        request.name(),
                        request.url()
                ))
                .toList();

        return taskArtifactRepository.saveAll(artifacts);
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task를 찾을 수 없습니다."));
    }

    private AgentReport findReport(Long reportId) {
        return agentReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 결과 보고를 찾을 수 없습니다."));
    }

    private void validateReportBelongsToTask(
            AgentReport report,
            Long taskId
    ) {
        if (!report.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Task와 결과 보고가 일치하지 않습니다.");
        }
    }
}