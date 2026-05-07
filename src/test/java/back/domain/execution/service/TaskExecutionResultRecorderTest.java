package back.domain.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.execution.entity.ExecutionTaskArtifact;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.repository.ExecutionAgentReportRepository;
import back.domain.execution.repository.ExecutionTaskArtifactRepository;
import back.domain.gateway.client.OpenClawChatResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskExecutionResultRecorderTest {

    @Mock
    private AgentExecutionResultParser agentExecutionResultParser;

    @Mock
    private ExecutionAgentReportRepository agentReportRepository;

    @Mock
    private ExecutionTaskArtifactRepository taskArtifactRepository;

    @InjectMocks
    private TaskExecutionResultRecorder recorder;

    private TaskExecution execution;

    @BeforeEach
    void setUp() {
        execution = TaskExecution.queued(1L, 2L, 3L, "openclaw-agent-1", null, "ai/task-2");
        ReflectionTestUtils.setField(execution, "id", 10L);
    }

    @Test
    @DisplayName("성공 결과 보고와 산출물을 taskExecutionId 기준으로 저장한다")
    void recordResult_savesReportAndArtifacts() {
        // given
        AgentReportSaveRequest report =
                new AgentReportSaveRequest("COMPLETED", "완료", "상세 내용", "리뷰하세요.");
        TaskArtifactSaveRequest artifact =
                new TaskArtifactSaveRequest("PR_URL", "생성된 PR", "https://github.com/example/repo/pull/1");
        AgentExecutionResult result = new AgentExecutionResult(report, List.of(artifact));

        // when
        recorder.recordResult(execution, result);

        // then
        ArgumentCaptor<ExecutionAgentReport> reportCaptor = ArgumentCaptor.forClass(ExecutionAgentReport.class);
        ArgumentCaptor<ExecutionTaskArtifact> artifactCaptor = ArgumentCaptor.forClass(ExecutionTaskArtifact.class);
        verify(agentReportRepository).save(reportCaptor.capture());
        verify(taskArtifactRepository).save(artifactCaptor.capture());
        assertThat(reportCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(reportCaptor.getValue().getSummary()).isEqualTo("완료");
        assertThat(artifactCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("PR_URL");
    }

    @Test
    @DisplayName("Agent 실행 결과 텍스트를 파싱한다")
    void parse_delegatesToParser() {
        // given
        AgentExecutionResult result = new AgentExecutionResult(
                new AgentReportSaveRequest("COMPLETED", "완료", "상세 내용", null),
                List.of());
        given(agentExecutionResultParser.parse("final text")).willReturn(result);

        // when
        AgentExecutionResult parsed = recorder.parse(new OpenClawChatResult("session-1", "final text"));

        // then
        assertThat(parsed).isSameAs(result);
    }

    @Test
    @DisplayName("실패 결과는 실패 보고서로 저장한다")
    void recordFailure_savesFailedReport() {
        // given
        execution.markFailed("Gateway timeout");

        // when
        recorder.recordFailure(execution);

        // then
        ArgumentCaptor<ExecutionAgentReport> reportCaptor = ArgumentCaptor.forClass(ExecutionAgentReport.class);
        verify(agentReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(reportCaptor.getValue().getDetail()).isEqualTo("Gateway timeout");
    }
}
