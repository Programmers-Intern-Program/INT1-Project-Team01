package back.domain.chat.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.service.ChatTaskExecutionDispatcher;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class ChatControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @MockitoBean
    private ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;

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
    @DisplayName("프론트 채팅 요청으로 선택 Agent Task 실행을 시작하고 메시지를 반환한다")
    void sendMessage() throws Exception {
        // given
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "프론트 채팅으로 Agent 실행해줘",
                4L,
                3L,
                TaskType.OTHER,
                TaskPriority.MEDIUM,
                null,
                false);

        // when & then
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.assignedAgentId").value(4L))
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("프론트 채팅으로 Agent 실행해줘"));
    }

    @Test
    @DisplayName("프론트 채팅 요청의 필수값을 검증한다")
    void validateSendMessageRequest() throws Exception {
        ChatMessageSendRequest request = new ChatMessageSendRequest(" ", 0L, null, null, null, null, false);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("채팅 메시지를 polling 방식으로 조회할 수 있다")
    void getMessages() throws Exception {
        // given
        Long taskId = sendChatMessageAndGetTaskId();

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/chat/tasks/{taskId}/messages", workspaceId, taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].role").value("USER"));
    }

    private Long sendChatMessageAndGetTaskId() throws Exception {
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "polling 조회용 메시지", 4L, null, null, null, null, false);
        String responseBody = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/chat/messages", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.get("taskId").asLong();
    }

}
