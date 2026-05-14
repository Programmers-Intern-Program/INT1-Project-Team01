package back.domain.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import back.domain.artifact.dto.ArtifactFileSaveCommand;
import back.domain.artifact.dto.StoredArtifactFile;
import back.domain.artifact.service.WorkspaceArtifactStorage;
import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import back.domain.execution.entity.ExecutionAgentReport;
import back.domain.execution.entity.ExecutionTaskArtifact;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.repository.ExecutionAgentReportRepository;
import back.domain.execution.repository.ExecutionTaskArtifactRepository;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.task.entity.TaskMessage;
import back.domain.task.entity.TaskMessageRole;
import back.domain.task.repository.TaskMessageRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.nio.file.Path;
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

    @Mock
    private TaskMessageRepository taskMessageRepository;

    @Mock
    private WorkspaceArtifactStorage workspaceArtifactStorage;

    @InjectMocks
    private TaskExecutionResultRecorder recorder;

    private TaskExecution execution;

    @BeforeEach
    void setUp() {
        execution = TaskExecution.queued(1L, 2L, 3L, "openclaw-agent-1", null, "ai/task-2");
        ReflectionTestUtils.setField(execution, "id", 10L);
        execution.assignRuntimeContext("/tmp/agent-workspace", "workspace-1-execution-10");
    }

    @Test
    @DisplayName("Agent 파일 산출물을 project root에 저장하고 FILE_PATH 산출물로 기록한다")
    void recordResult_savesFilesAsArtifacts() {
        // given
        AgentReportSaveRequest report =
                new AgentReportSaveRequest("COMPLETED", "파일 생성 완료", "상세 내용", null);
        ArtifactFileSaveCommand file =
                new ArtifactFileSaveCommand("src/main/java/App.java", "class App {}");
        AgentExecutionResult result = new AgentExecutionResult(report, List.of(), List.of(file));
        given(workspaceArtifactStorage.storeFilesFromWorkspace(1L, Path.of("/tmp/agent-workspace"), List.of(file)))
                .willReturn(List.of(new StoredArtifactFile("src/main/java/App.java", 12)));

        // when
        recorder.recordResult(execution, result);

        // then
        ArgumentCaptor<ExecutionTaskArtifact> artifactCaptor = ArgumentCaptor.forClass(ExecutionTaskArtifact.class);
        ArgumentCaptor<TaskMessage> messageCaptor = ArgumentCaptor.forClass(TaskMessage.class);
        verify(taskArtifactRepository).save(artifactCaptor.capture());
        verify(taskMessageRepository).save(messageCaptor.capture());
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("FILE_PATH");
        assertThat(artifactCaptor.getValue().getName()).isEqualTo("src/main/java/App.java");
        assertThat(artifactCaptor.getValue().getUrl()).isEqualTo("src/main/java/App.java");
        assertThat(messageCaptor.getValue().getContent())
                .contains("[FILE_PATH] src/main/java/App.java");
    }

    @Test
    @DisplayName("파일 산출물 저장에 실패해도 보고서와 사용자 메시지는 저장한다")
    void recordResult_fileStorageFailed_savesReportAndWarningMessage() {
        // given
        AgentReportSaveRequest report =
                new AgentReportSaveRequest("COMPLETED", "파일 생성 완료", "상세 내용", null);
        ArtifactFileSaveCommand file = new ArtifactFileSaveCommand("../secret.txt", "secret");
        AgentExecutionResult result = new AgentExecutionResult(report, List.of(), List.of(file));
        given(workspaceArtifactStorage.storeFilesFromWorkspace(1L, Path.of("/tmp/agent-workspace"), List.of(file)))
                .willThrow(new ServiceException(
                        CommonErrorCode.BAD_REQUEST,
                        "invalid artifact path",
                        "산출물 파일 경로가 올바르지 않습니다."));

        // when
        recorder.recordResult(execution, result);

        // then
        ArgumentCaptor<ExecutionAgentReport> reportCaptor = ArgumentCaptor.forClass(ExecutionAgentReport.class);
        ArgumentCaptor<TaskMessage> messageCaptor = ArgumentCaptor.forClass(TaskMessage.class);
        verify(agentReportRepository).save(reportCaptor.capture());
        verify(taskArtifactRepository, never()).save(any(ExecutionTaskArtifact.class));
        verify(taskMessageRepository).save(messageCaptor.capture());
        assertThat(reportCaptor.getValue().getSummary()).isEqualTo("파일 생성 완료");
        assertThat(messageCaptor.getValue().getContent())
                .contains("파일 생성 완료", "산출물 저장 경고: 산출물 파일 경로가 올바르지 않습니다.");
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
        ArgumentCaptor<TaskMessage> messageCaptor = ArgumentCaptor.forClass(TaskMessage.class);
        verify(agentReportRepository).save(reportCaptor.capture());
        verify(taskArtifactRepository).save(artifactCaptor.capture());
        verify(taskMessageRepository).save(messageCaptor.capture());
        assertThat(reportCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(reportCaptor.getValue().getSummary()).isEqualTo("완료");
        assertThat(artifactCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("PR_URL");
        assertThat(messageCaptor.getValue().getWorkspaceId()).isEqualTo(1L);
        assertThat(messageCaptor.getValue().getTaskId()).isEqualTo(2L);
        assertThat(messageCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(messageCaptor.getValue().getRole()).isEqualTo(TaskMessageRole.ASSISTANT);
        assertThat(messageCaptor.getValue().getContent())
                .contains("완료", "상세 내용", "권장 조치: 리뷰하세요.", "산출물", "[PR_URL] 생성된 PR");
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
        ArgumentCaptor<TaskMessage> messageCaptor = ArgumentCaptor.forClass(TaskMessage.class);
        verify(agentReportRepository).save(reportCaptor.capture());
        verify(taskMessageRepository).save(messageCaptor.capture());
        assertThat(reportCaptor.getValue().getTaskExecutionId()).isEqualTo(10L);
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(reportCaptor.getValue().getDetail()).isEqualTo("Gateway timeout");
        assertThat(messageCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(messageCaptor.getValue().getContent())
                .contains(
                        "Agent 실행에 실패했습니다.",
                        "Gateway timeout",
                        "Gateway 설정과 OpenClaw Agent 실행 상태를 확인하세요.");
    }
}
