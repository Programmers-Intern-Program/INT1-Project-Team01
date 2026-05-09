package back.domain.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.entity.ChatMessageRole;
import back.domain.chat.service.ChatService;
import back.domain.slack.event.SlackReplyRequestedEvent;
import back.domain.task.entity.TaskStatus;

@ExtendWith(MockitoExtension.class)
class SlackConversationAdapterTest {

    @InjectMocks
    private SlackConversationAdapter adapter;

    @Mock
    private ChatService chatService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Slack 일반 응답은 Slack reply 이벤트로 발행하고 최종 응답을 반환한다")
    void sendMessage_delegatesToChatService() {
        // given
        given(chatService.sendSlackMessage(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(new ChatMessageSendResponse(
                        10L,
                        null,
                        1L,
                        2L,
                        null,
                        null,
                        null,
                        "Agent 응답",
                        null,
                        null,
                        List.of(new ChatMessageResponse(
                                20L, 10L, null, null, ChatMessageRole.ASSISTANT, "Agent 응답", null))));

        // when
        String finalText = adapter.sendMessage(1L, "T123:C123:999.000", "backend-agent", "로그인 API 구현해줘");

        // then
        ArgumentCaptor<SlackChatMessageSendCommand> captor =
                ArgumentCaptor.forClass(SlackChatMessageSendCommand.class);
        verify(chatService).sendSlackMessage(eq(1L), captor.capture());
        assertThat(finalText).isEqualTo("Agent 응답");
        assertThat(captor.getValue().sourceRef()).isEqualTo("T123:C123:999.000");
        assertThat(captor.getValue().agentName()).isEqualTo("backend-agent");
        assertThat(captor.getValue().message()).isEqualTo("로그인 API 구현해줘");

        ArgumentCaptor<SlackReplyRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(SlackReplyRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sourceRef()).isEqualTo("T123:C123:999.000");
        assertThat(eventCaptor.getValue().message()).isEqualTo("Agent 응답");
        assertThat(eventCaptor.getValue().deduplicationKey()).isEqualTo("slack-chat-message-20");
    }

    @Test
    @DisplayName("Task로 분기된 Slack 응답은 즉시 reply 이벤트를 발행하지 않는다")
    void sendMessage_taskResponseDoesNotPublishImmediateReply() {
        // given
        given(chatService.sendSlackMessage(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(new ChatMessageSendResponse(
                        10L,
                        30L,
                        1L,
                        2L,
                        TaskStatus.IN_PROGRESS,
                        null,
                        null,
                        "작업을 시작하겠습니다.",
                        null,
                        null,
                        List.of()));

        // when
        String finalText = adapter.sendMessage(1L, "T123:C123:999.000", null, "로그인 API 구현해줘");

        // then
        assertThat(finalText).isEqualTo("작업을 시작하겠습니다.");
        verify(eventPublisher, never()).publishEvent(any());
    }
}
