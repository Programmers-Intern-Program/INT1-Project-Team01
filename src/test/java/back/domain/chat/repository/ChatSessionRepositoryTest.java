package back.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import back.domain.chat.entity.ChatSession;
import back.domain.chat.entity.ChatSessionSource;
import back.domain.chat.entity.ChatSessionStatus;
import back.global.security.crypto.TinkCryptoUtil;

@DataJpaTest
class ChatSessionRepositoryTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @MockitoBean
    private TinkCryptoUtil tinkCryptoUtil;

    @Test
    @DisplayName("Slack thread sourceRef로 ChatSession을 조회한다")
    void findByWorkspaceIdAndSourceAndSourceRef_returnsSession() {
        // given
        ChatSession session = chatSessionRepository.save(ChatSession.start(
                1L, 2L, ChatSessionSource.SLACK, "T1:C1:1710000000.000000", "workspace-1-slack-thread-1"));

        // when
        ChatSession found = chatSessionRepository
                .findByWorkspaceIdAndSourceAndSourceRef(1L, ChatSessionSource.SLACK, "T1:C1:1710000000.000000")
                .orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(session.getId());
        assertThat(found.getOpenClawSessionKey()).isEqualTo("workspace-1-slack-thread-1");
    }

    @Test
    @DisplayName("Workspace, Agent, 상태 기준으로 활성 ChatSession을 조회한다")
    void findByWorkspaceIdAndAgentIdAndStatusOrderByLastMessageAtDescIdDesc_returnsActiveSessions() {
        // given
        ChatSession active = chatSessionRepository.save(
                ChatSession.start(1L, 2L, ChatSessionSource.WEB, "web-session-1", "workspace-1-web-session-1"));
        ChatSession closed = chatSessionRepository.save(
                ChatSession.start(1L, 2L, ChatSessionSource.WEB, "web-session-2", "workspace-1-web-session-2"));
        closed.close();
        chatSessionRepository.save(closed);

        // when
        List<ChatSession> sessions =
                chatSessionRepository.findByWorkspaceIdAndAgentIdAndStatusOrderByLastMessageAtDescIdDesc(
                        1L, 2L, ChatSessionStatus.ACTIVE);

        // then
        assertThat(sessions).extracting(ChatSession::getId).containsExactly(active.getId());
    }
}
