package back.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.entity.TaskMessageRole;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskStatus;
import back.domain.task.entity.TaskType;
import back.domain.task.repository.TaskRepository;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;

    private Long workspaceId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.createUser(
                "test-google-sub-" + UUID.randomUUID(), "test-" + UUID.randomUUID() + "@test.com", "테스트 멤버"));
        Workspace workspace = workspaceRepository.save(Workspace.create("테스트 워크스페이스", "테스트용 워크스페이스입니다.", member));
        workspaceId = workspace.getId();
    }

    @Test
    @DisplayName("채팅 메시지를 선택 Agent Task 실행으로 변환하고 메시지 목록을 반환한다")
    void sendMessage_createsTaskRunAndReturnsMessages() {
        // given
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "선택한 Agent로 작업 실행해줘",
                4L,
                3L,
                TaskType.OTHER,
                TaskPriority.MEDIUM,
                null,
                false);

        // when
        ChatMessageSendResponse response = chatService.sendMessage(workspaceId, request);

        // then
        assertThat(response.taskId()).isNotNull();
        assertThat(response.assignedAgentId()).isEqualTo(4L);
        assertThat(response.taskStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.executionStatus()).isNull();
        assertThat(response.taskExecutionId()).isNull();
        assertThat(response.messages())
                .extracting(TaskMessageResponse::role, TaskMessageResponse::content)
                .containsExactly(tuple(TaskMessageRole.USER, "선택한 Agent로 작업 실행해줘"));
        verify(chatTaskExecutionDispatcher).run(eq(workspaceId), eq(response.taskId()), eq(false));
    }

    @Test
    @DisplayName("채팅 메시지 polling 조회는 Task 메시지 목록을 반환한다")
    void getMessages_returnsTaskMessages() {
        // given
        ChatMessageSendResponse sent = chatService.sendMessage(
                workspaceId,
                new ChatMessageSendRequest("조회할 메시지", 4L, null, null, null, null, false));

        // when
        List<TaskMessageResponse> messages = chatService.getMessages(workspaceId, sent.taskId());

        // then
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().role()).isEqualTo(TaskMessageRole.USER);
        assertThat(messages.getFirst().content()).isEqualTo("조회할 메시지");
    }

    @Test
    @DisplayName("채팅 실행 디스패치가 거절되면 Task를 FAILED로 전환하고 실패 응답을 반환한다")
    void sendMessage_marksTaskFailedWhenDispatchRejected() {
        // given
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "큐 포화 실패 검증",
                4L,
                null,
                TaskType.OTHER,
                TaskPriority.LOW,
                null,
                false);
        willThrow(new RejectedExecutionException("queue full"))
                .given(chatTaskExecutionDispatcher)
                .run(eq(workspaceId), anyLong(), eq(false));

        // when & then
        assertThatThrownBy(() -> chatService.sendMessage(workspaceId, request))
                .isInstanceOfSatisfying(ServiceException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getClientMessage()).contains("Agent 실행을 시작하지 못했습니다.");
                });
        assertThat(taskRepository.findByWorkspaceId(workspaceId, PageRequest.of(0, 10)).getContent())
                .hasSize(1)
                .first()
                .extracting("status")
                .isEqualTo(TaskStatus.FAILED);
    }
}
