package back.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatAgentIntentParserTest {

    private final ChatAgentIntentParser parser = new ChatAgentIntentParser();

    @Test
    @DisplayName("JSON이 아닌 Agent 응답은 일반 채팅으로 처리한다")
    void parse_plainText_returnsChatIntent() {
        // when
        ChatAgentIntent intent = parser.parse("일반 답변입니다.");

        // then
        assertThat(intent.type()).isEqualTo(ChatAgentIntentType.CHAT);
        assertThat(intent.message()).isEqualTo("일반 답변입니다.");
        assertThat(intent.task()).isNull();
    }

    @Test
    @DisplayName("CHAT intent JSON은 message를 일반 채팅 응답으로 사용한다")
    void parse_chatJson_returnsChatIntent() {
        // when
        ChatAgentIntent intent = parser.parse("""
                {
                  "intent": "CHAT",
                  "message": "일반 대화 응답입니다."
                }
                """);

        // then
        assertThat(intent.type()).isEqualTo(ChatAgentIntentType.CHAT);
        assertThat(intent.message()).isEqualTo("일반 대화 응답입니다.");
        assertThat(intent.task()).isNull();
    }

    @Test
    @DisplayName("TASK intent JSON은 Task 생성 명세를 추출한다")
    void parse_taskJson_returnsTaskIntent() {
        // when
        ChatAgentIntent intent = parser.parse("""
                {
                  "intent": "TASK",
                  "message": "작업을 시작하겠습니다.",
                  "task": {
                    "title": "로그인 API 구현",
                    "description": "로그인 API와 테스트 코드를 작성합니다.",
                    "taskType": "FEATURE_IMPLEMENTATION",
                    "priority": "HIGH",
                    "repositoryId": 3,
                    "createPr": true
                  }
                }
                """);

        // then
        assertThat(intent.type()).isEqualTo(ChatAgentIntentType.TASK);
        assertThat(intent.message()).isEqualTo("작업을 시작하겠습니다.");
        assertThat(intent.task().title()).isEqualTo("로그인 API 구현");
        assertThat(intent.task().description()).isEqualTo("로그인 API와 테스트 코드를 작성합니다.");
        assertThat(intent.task().taskType()).isEqualTo(TaskType.FEATURE_IMPLEMENTATION);
        assertThat(intent.task().priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(intent.task().repositoryId()).isEqualTo(3L);
        assertThat(intent.task().createPr()).isTrue();
    }
}
