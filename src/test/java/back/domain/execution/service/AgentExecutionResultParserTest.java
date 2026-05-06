package back.domain.execution.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentExecutionResultParserTest {

    private final AgentExecutionResultParser parser = new AgentExecutionResultParser();

    @Test
    @DisplayName("Agent final report JSON을 결과 보고와 산출물 저장 요청으로 파싱한다")
    void parse_jsonReport_success() {
        // given
        String finalText = """
                {
                  "status": "COMPLETED",
                  "summary": "PR 생성 완료",
                  "detail": "브랜치 생성과 커밋을 완료했습니다.",
                  "recommendedAction": "PR을 리뷰해주세요.",
                  "artifacts": [
                    {
                      "artifactType": "PR_URL",
                      "name": "생성된 PR",
                      "url": "https://github.com/example/repo/pull/1"
                    }
                  ]
                }
                """;

        // when
        AgentExecutionResult result = parser.parse(finalText);

        // then
        assertThat(result.report().status()).isEqualTo("COMPLETED");
        assertThat(result.report().summary()).isEqualTo("PR 생성 완료");
        assertThat(result.report().detail()).isEqualTo("브랜치 생성과 커밋을 완료했습니다.");
        assertThat(result.report().recommendedAction()).isEqualTo("PR을 리뷰해주세요.");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().artifactType()).isEqualTo("PR_URL");
        assertThat(result.artifacts().getFirst().name()).isEqualTo("생성된 PR");
    }

    @Test
    @DisplayName("Markdown json fence 안의 nested report도 파싱한다")
    void parse_fencedNestedReport_success() {
        // given
        String finalText = """
                완료했습니다.
                ```json
                {
                  "report": {
                    "status": "COMPLETED",
                    "summary": "작업 완료",
                    "detail": "테스트까지 완료했습니다."
                  }
                }
                ```
                """;

        // when
        AgentExecutionResult result = parser.parse(finalText);

        // then
        assertThat(result.report().summary()).isEqualTo("작업 완료");
        assertThat(result.report().detail()).isEqualTo("테스트까지 완료했습니다.");
        assertThat(result.artifacts()).isEmpty();
    }

    @Test
    @DisplayName("JSON이 아니면 final text를 fallback report로 저장한다")
    void parse_plainText_fallbackReport() {
        // when
        AgentExecutionResult result = parser.parse("작업을 완료했습니다.");

        // then
        assertThat(result.report().status()).isEqualTo("COMPLETED");
        assertThat(result.report().summary()).isEqualTo("작업을 완료했습니다.");
        assertThat(result.report().detail()).isEqualTo("작업을 완료했습니다.");
        assertThat(result.artifacts()).isEmpty();
    }
}
