package back.domain.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.service.ChatService;

@ExtendWith(MockitoExtension.class)
class SlackConversationAdapterTest {

    @InjectMocks
    private SlackConversationAdapter adapter;

    @Mock
    private ChatService chatService;

    @Test
    @DisplayName("Slack 메시지를 ChatService Slack 전용 명령으로 위임하고 최종 응답을 반환한다")
    void sendMessage_delegatesToChatService() {
        // given
        given(chatService.sendSlackMessage(eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(new ChatMessageSendResponse(
                        10L, null, 1L, 2L, null, null, null, "Agent 응답", null, null, List.of()));

        // when
        String finalText = adapter.sendMessage(1L, "T123:C123:999.000", "로그인 API 구현해줘");

        // then
        ArgumentCaptor<SlackChatMessageSendCommand> captor =
                ArgumentCaptor.forClass(SlackChatMessageSendCommand.class);
        verify(chatService).sendSlackMessage(eq(1L), captor.capture());
        assertThat(finalText).isEqualTo("Agent 응답");
        assertThat(captor.getValue().sourceRef()).isEqualTo("T123:C123:999.000");
        assertThat(captor.getValue().message()).isEqualTo("로그인 API 구현해줘");
    }
}
