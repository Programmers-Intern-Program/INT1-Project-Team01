package back.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import back.domain.agent.entity.AgentCategory;
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

    @Test
    @DisplayName("코드펜스에 감싼 TASK intent JSON도 파싱한다")
    void parse_fencedTaskJson_returnsTaskIntent() {
        // when
        ChatAgentIntent intent = parser.parse("""
                아래 JSON으로 처리하면 됩니다.

                ```json
                {
                  "intent": "TASK",
                  "message": "문서 작업을 시작하겠습니다.",
                  "task": {
                    "title": "README 정리",
                    "taskType": "DOCUMENTATION"
                  }
                }
                ```
                """);

        // then
        assertThat(intent.type()).isEqualTo(ChatAgentIntentType.TASK);
        assertThat(intent.message()).isEqualTo("문서 작업을 시작하겠습니다.");
        assertThat(intent.task().title()).isEqualTo("README 정리");
        assertThat(intent.task().taskType()).isEqualTo(TaskType.DOCUMENTATION);
    }

    @Test
    @DisplayName("설명 문구 사이에 있는 TASK intent JSON도 파싱한다")
    void parse_embeddedTaskJson_returnsTaskIntent() {
        // when
        ChatAgentIntent intent = parser.parse("""
                요청을 작업으로 처리합니다.
                {"intent":"TASK","message":"테스트를 작성하겠습니다.","task":{"title":"테스트 작성"}}
                작업을 시작할게요.
                """);

        // then
        assertThat(intent.type()).isEqualTo(ChatAgentIntentType.TASK);
        assertThat(intent.message()).isEqualTo("테스트를 작성하겠습니다.");
        assertThat(intent.task().title()).isEqualTo("테스트 작성");
    }

    @Test
    @DisplayName("ORCHESTRATE intent JSON은 Orchestrator 계획 명세를 추출한다")
    void parse_orchestrateJson_returnsOrchestrationIntent() {
        // when
        ChatAgentIntent intent = parser.parse("""
                {
                  "intent": "ORCHESTRATE",
                  "message": "작업 계획을 저장했습니다.",
                  "plan": {
                    "title": "회원가입 기능 구현 계획",
                    "steps": [
                      {
                        "stepKey": "backend-1",
                        "agentId": 2,
                        "agentName": "backend-agent",
                        "category": "BACKEND",
                        "title": "회원가입 API 구현",
                        "prompt": "회원가입 API와 테스트를 구현하세요.",
                        "dependsOn": []
                      },
                      {
                        "stepKey": "frontend-1",
                        "agentName": "frontend-agent",
                        "category": "FRONTEND",
                        "title": "회원가입 화면 연동",
                        "prompt": "회원가입 API와 화면을 연동하세요.",
                        "dependsOn": ["backend-1"]
                      }
                    ]
                  }
                }
                """);

        // then
        assertThat(intent.type()).isEqualTo(ChatAgentIntentType.ORCHESTRATE);
        assertThat(intent.message()).isEqualTo("작업 계획을 저장했습니다.");
        assertThat(intent.orchestrationPlan().title()).isEqualTo("회원가입 기능 구현 계획");
        assertThat(intent.orchestrationPlan().steps()).hasSize(2);
        assertThat(intent.orchestrationPlan().steps().get(0).stepKey()).isEqualTo("backend-1");
        assertThat(intent.orchestrationPlan().steps().get(0).agentId()).isEqualTo(2L);
        assertThat(intent.orchestrationPlan().steps().get(0).category()).isEqualTo(AgentCategory.BACKEND);
        assertThat(intent.orchestrationPlan().steps().get(1).dependsOn()).containsExactly("backend-1");
    }
}
