package back.domain.slack.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlackAgentCommandParserTest {

    private final SlackAgentCommandParser parser = new SlackAgentCommandParser();

    @Test
    @DisplayName("/agent 문법이 있으면 Agent 이름과 메시지를 분리한다")
    void parse_agentCommand_returnsAgentNameAndMessage() {
        // when
        SlackAgentCommandParser.ParsedCommand command =
                parser.parse("/agent backend-agent 로그인 API 구현해줘");

        // then
        assertThat(command.agentName()).isEqualTo("backend-agent");
        assertThat(command.message()).isEqualTo("로그인 API 구현해줘");
    }

    @Test
    @DisplayName("/agent 문법이 없으면 원문 메시지만 반환한다")
    void parse_plainText_returnsMessageOnly() {
        // when
        SlackAgentCommandParser.ParsedCommand command = parser.parse("로그인 API 구현해줘");

        // then
        assertThat(command.agentName()).isNull();
        assertThat(command.message()).isEqualTo("로그인 API 구현해줘");
    }
}
