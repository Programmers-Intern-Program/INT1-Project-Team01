package back.domain.task.controller;

import back.domain.task.domain.SourceType;
import back.domain.task.domain.TaskPriority;
import back.domain.task.domain.TaskStatus;
import back.domain.task.domain.TaskType;
import back.domain.task.dto.request.TaskCreateRequest;
import back.domain.task.dto.request.TaskStatusUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class TaskControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private final Long workspaceId = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build();

        objectMapper = new ObjectMapper();
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

        TaskStatusUpdateRequest request = new TaskStatusUpdateRequest(
                TaskStatus.IN_PROGRESS,
                "Agent 작업 시작"
        );

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/status", workspaceId, taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.previousStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"));
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
                "이 PR 리뷰해줘"
        );
    }
}