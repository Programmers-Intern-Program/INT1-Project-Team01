package back.domain.execution.service;

import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.entity.AgentReport;
import back.domain.execution.entity.TaskArtifact;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.repository.AgentReportRepository;
import back.domain.execution.repository.TaskArtifactRepository;
import back.domain.gateway.client.OpenClawChatResult;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskExecutionResultRecorder {

    private static final String FAILED_STATUS = "FAILED";
    private static final String FAILED_SUMMARY = "Agent 실행에 실패했습니다.";

    private final AgentExecutionResultParser agentExecutionResultParser;
    private final AgentReportRepository agentReportRepository;
    private final TaskArtifactRepository taskArtifactRepository;

    public void recordSuccess(TaskExecution execution, OpenClawChatResult chatResult) {
        Objects.requireNonNull(execution);
        Objects.requireNonNull(chatResult);
        AgentExecutionResult result = agentExecutionResultParser.parse(chatResult.finalText());
        agentReportRepository.save(AgentReport.create(execution.getId(), result.report()));
        result.artifacts().stream()
                .map(artifact -> TaskArtifact.create(execution.getId(), artifact))
                .forEach(taskArtifactRepository::save);
    }

    public void recordFailure(TaskExecution execution) {
        Objects.requireNonNull(execution);
        agentReportRepository.save(AgentReport.create(
                execution.getId(),
                new AgentReportSaveRequest(
                        FAILED_STATUS,
                        FAILED_SUMMARY,
                        execution.getFailureReason(),
                        "Gateway 설정과 OpenClaw Agent 실행 상태를 확인하세요.")));
    }
}
