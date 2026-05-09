package back.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import back.domain.chat.entity.ChatMessage;
import back.domain.chat.entity.ChatMessageRole;
import back.global.security.crypto.TinkCryptoUtil;

@DataJpaTest
class ChatMessageRepositoryTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockitoBean
    private TinkCryptoUtil tinkCryptoUtil;

    @Test
    @DisplayName("ChatSession 메시지를 생성 순서로 조회한다")
    void findByWorkspaceIdAndChatSessionIdOrderByCreatedAtAscIdAsc_returnsOrderedMessages() {
        // given
        chatMessageRepository.save(ChatMessage.user(1L, 10L, "첫 메시지"));
        chatMessageRepository.save(ChatMessage.assistant(1L, 10L, "첫 응답"));
        chatMessageRepository.save(ChatMessage.user(1L, 11L, "다른 세션 메시지"));

        // when
        List<ChatMessage> messages =
                chatMessageRepository.findByWorkspaceIdAndChatSessionIdOrderByCreatedAtAscIdAsc(1L, 10L);

        // then
        assertThat(messages)
                .extracting(ChatMessage::getRole, ChatMessage::getContent)
                .containsExactly(tuple(ChatMessageRole.USER, "첫 메시지"), tuple(ChatMessageRole.ASSISTANT, "첫 응답"));
    }

    @Test
    @DisplayName("ChatSession 안에서 특정 Task와 연결된 메시지를 조회한다")
    void findByWorkspaceIdAndChatSessionIdAndTaskIdOrderByCreatedAtAscIdAsc_returnsTaskMessages() {
        // given
        chatMessageRepository.save(ChatMessage.user(1L, 10L, "일반 대화"));
        chatMessageRepository.save(ChatMessage.assistantForTask(1L, 10L, 20L, 30L, "작업 완료"));

        // when
        List<ChatMessage> messages =
                chatMessageRepository.findByWorkspaceIdAndChatSessionIdAndTaskIdOrderByCreatedAtAscIdAsc(1L, 10L, 20L);

        // then
        assertThat(messages)
                .extracting(ChatMessage::getTaskId, ChatMessage::getTaskExecutionId, ChatMessage::getContent)
                .containsExactly(tuple(20L, 30L, "작업 완료"));
    }
}
