package back.domain.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;
import back.domain.execution.entity.ExecutionTaskArtifact;
import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.execution.repository.ExecutionTaskArtifactRepository;
import back.domain.execution.repository.TaskExecutionRepository;
import back.domain.execution.service.TaskExecutionRunner;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskMessage;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.task.repository.TaskMessageRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class TaskControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TaskExecutionRepository taskExecutionRepository;

    @Autowired
    private ExecutionTaskArtifactRepository executionTaskArtifactRepository;

    @Autowired
    private TaskMessageRepository taskMessageRepository;

    @MockitoBean
    private TaskExecutionRunner taskExecutionRunner;

    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        objectMapper = new ObjectMapper();

        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(), "test-" + UUID.randomUUID() + "@test.com", "테스트 멤버"));

        Workspace workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "테스트용 워크스페이스입니다.", member));

        workspaceId = workspace.getId();
    }

    @Test
    @DisplayName("Task를 생성할 수 있다")
    void createTask() throws Exception {
        TaskCreateRequest request = createRequest();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.title").value("PR 리뷰"))
                .andExpect(jsonPath("$.taskType").value("PR_REVIEW"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("워크스페이스별 Task 목록을 조회할 수 있다")
    void getTasks() throws Exception {
        createTaskAndGetId();

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].title").value("PR 리뷰"))
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"));
    }

    @Test
    @DisplayName("Task 상세 정보를 조회할 수 있다")
    void getTaskDetail() throws Exception {
        Long taskId = createTaskAndGetId();

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.title").value("PR 리뷰"))
                .andExpect(jsonPath("$.description").value("최근 PR 변경사항을 리뷰한다."))
                .andExpect(jsonPath("$.taskType").value("PR_REVIEW"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("Task 상태를 변경할 수 있다")
    void updateTaskStatus() throws Exception {
        Long taskId = createTaskAndGetId();

        TaskStatusUpdateRequest request = new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS, "Agent 작업 시작");

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/status", workspaceId, taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.previousStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Task를 생성하고 Runner 실행까지 연결할 수 있다")
    void createAndRunTask() throws Exception {
        given(taskExecutionRunner.run(any(TaskExecutionRunCommand.class))).willAnswer(invocation -> {
            TaskExecutionRunCommand command = invocation.getArgument(0);
            return new TaskExecutionRunResult(
                    20L,
                    command.taskId(),
                    command.workspaceId(),
                    100L,
                    TaskExecutionStatus.SUCCEEDED,
                    "/tmp/aioffice/workspaces/1/executions/20/repo",
                    "workspace-1-execution-20",
                    "작업을 완료했습니다.",
                    null);
        });

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks/run", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRunRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.title").value("PR 리뷰"))
                .andExpect(jsonPath("$.taskStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.taskExecutionId").value(20L))
                .andExpect(jsonPath("$.executionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.finalText").value("작업을 완료했습니다."));
    }

    @Test
    @DisplayName("Task 실행 요청의 필수값과 양수 ID를 검증한다")
    void validateTaskRunRequest() throws Exception {
        TaskRunRequest request = new TaskRunRequest(
                "PR 리뷰",
                "최근 PR 변경사항을 리뷰한다.",
                TaskType.PR_REVIEW,
                null,
                0L,
                0L,
                SourceType.DASHBOARD,
                "dashboard-test",
                "이 PR 리뷰해줘",
                true);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks/run", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Task 실행 응답 메시지를 조회할 수 있다")
    void getTaskMessages() throws Exception {
        Long taskId = createTaskAndGetId();
        TaskExecution execution = taskExecutionRepository.save(TaskExecution.queued(
                workspaceId,
                taskId,
                1L,
                "openclaw-agent-1",
                3L,
                "feature/pr-review"));
        executionTaskArtifactRepository.save(ExecutionTaskArtifact.create(
                execution.getId(),
                new TaskArtifactSaveRequest(
                        "PR_URL",
                        "생성된 PR",
                        "https://github.com/example/repo/pull/1")));
        taskMessageRepository.save(TaskMessage.assistantResponse(
                workspaceId,
                taskId,
                execution.getId(),
                "COMPLETED",
                "PR 리뷰가 완료되었습니다.\n\n산출물\n- [PR_URL] 생성된 PR",
                "PR 리뷰가 완료되었습니다.",
                "입력값 검증 개선 포인트를 확인했습니다.",
                "DTO validation 추가를 권장합니다."));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/messages", workspaceId, taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(taskId))
                .andExpect(jsonPath("$[0].taskExecutionId").value(execution.getId()))
                .andExpect(jsonPath("$[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].summary").value("PR 리뷰가 완료되었습니다."))
                .andExpect(jsonPath("$[0].artifacts[0].artifactType").value("PR_URL"))
                .andExpect(jsonPath("$[0].artifacts[0].name").value("생성된 PR"));
    }

    private Long createTaskAndGetId() throws Exception {
        TaskCreateRequest request = createRequest();

        String responseBody = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(responseBody);

        assertThat(jsonNode.get("taskId")).isNotNull();

        return jsonNode.get("taskId").asLong();
    }

    private TaskCreateRequest createRequest() {
        return new TaskCreateRequest(
                "PR 리뷰",
                "최근 PR 변경사항을 리뷰한다.",
                TaskType.PR_REVIEW,
                TaskPriority.HIGH,
                1L,
                3L,
                SourceType.DASHBOARD,
                "dashboard-test",
                "이 PR 리뷰해줘");
    }

    private TaskRunRequest createRunRequest() {
        return new TaskRunRequest(
                "PR 리뷰",
                "최근 PR 변경사항을 리뷰한다.",
                TaskType.PR_REVIEW,
                TaskPriority.HIGH,
                1L,
                3L,
                SourceType.DASHBOARD,
                "dashboard-test",
                "이 PR 리뷰해줘",
                true);
    }
}
