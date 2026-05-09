package back.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatSessionTest {

    @Test
    @DisplayName("ChatSession은 입력 채널과 고정 OpenClaw sessionKey를 저장한다")
    void start_storesSourceAndOpenClawSessionKey() {
        // when
        ChatSession session = ChatSession.start(
                1L, 2L, ChatSessionSource.SLACK, "T1:C1:1710000000.000000", "workspace-1-slack-T1-C1-1710000000");

        // then
        assertThat(session.getWorkspaceId()).isEqualTo(1L);
        assertThat(session.getAgentId()).isEqualTo(2L);
        assertThat(session.getSource()).isEqualTo(ChatSessionSource.SLACK);
        assertThat(session.getSourceRef()).isEqualTo("T1:C1:1710000000.000000");
        assertThat(session.getOpenClawSessionKey()).isEqualTo("workspace-1-slack-T1-C1-1710000000");
        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
        assertThat(session.getLastMessageAt()).isNotNull();
    }

    @Test
    @DisplayName("ChatSession은 sourceRef가 없는 웹 신규 대화도 표현할 수 있다")
    void start_nullSourceRef_success() {
        // when
        ChatSession session = ChatSession.start(1L, 2L, ChatSessionSource.WEB, null, "workspace-1-web-session-1");

        // then
        assertThat(session.getSourceRef()).isNull();
    }

    @Test
    @DisplayName("ChatSession은 close/reopen으로 세션 상태를 변경한다")
    void closeAndReopen_updatesStatus() {
        // given
        ChatSession session = ChatSession.start(1L, 2L, ChatSessionSource.WEB, "web-session-1", "openclaw-session-1");

        // when
        session.close();

        // then
        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.CLOSED);

        // when
        session.reopen();

        // then
        assertThat(session.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("OpenClaw sessionKey는 필수값이다")
    void start_blankOpenClawSessionKey_throwsException() {
        assertThatThrownBy(() -> ChatSession.start(1L, 2L, ChatSessionSource.WEB, "web-session-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openClawSessionKey");
    }
}
