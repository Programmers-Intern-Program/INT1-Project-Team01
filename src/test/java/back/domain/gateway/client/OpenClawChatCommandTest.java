package back.domain.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenClawChatCommandTest {

    @Test
    @DisplayName("sessionKey가 agent prefix를 갖지 않으면 OpenClaw full session key로 변환한다")
    void fullSessionKey_withoutPrefix_addsAgentPrefix() {
        // given
        OpenClawChatCommand command =
                new OpenClawChatCommand("openclaw-agent-1", "workspace-1-execution-10", "작업 실행", "idem-1");

        // when & then
        assertThat(command.fullSessionKey()).isEqualTo("agent:openclaw-agent-1:workspace-1-execution-10");
    }

    @Test
    @DisplayName("이미 full session key면 그대로 사용한다")
    void fullSessionKey_withPrefix_returnsOriginal() {
        // given
        OpenClawChatCommand command = new OpenClawChatCommand(
                "openclaw-agent-1", "agent:openclaw-agent-1:workspace-1-execution-10", "작업 실행", "idem-1");

        // when & then
        assertThat(command.fullSessionKey()).isEqualTo("agent:openclaw-agent-1:workspace-1-execution-10");
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 생성하지 않는다")
    void create_blankMessage_throwsException() {
        assertThatThrownBy(() -> new OpenClawChatCommand("openclaw-agent-1", "session", " ", "idem-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}
