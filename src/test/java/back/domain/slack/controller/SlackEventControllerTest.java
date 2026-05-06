package back.domain.slack.controller;

import back.domain.slack.filter.SlackSignatureVerificationFilter;
import back.domain.slack.service.SlackEventService;
import back.testUtil.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SlackEventController}에 대한 슬라이스 테스트입니다.
 * <p>
 * {@code SlackSignatureVerificationFilter}는 Mock으로 대체하여 필터를 통과시키고,
 * 컨트롤러의 비즈니스 로직(url_verification, event_callback 처리)만을 검증합니다.
 *
 * @author minhee
 * @since 2026-05-04
 */
@WebMvcTest(SlackEventController.class)
class SlackEventControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private SlackEventService slackEventService;

    @Test
    @DisplayName("url_verification 요청 시 challenge 값을 그대로 반환한다")
    void urlVerificationTest() throws Exception {
        // given
        // 필터가 실제 검증 없이 다음 필터로 바로 넘기도록 설정
        doAnswer(invocation -> {
            var request = invocation.getArgument(0);
            var response = invocation.getArgument(1);
            var chain = invocation.getArgument(2);
            ((jakarta.servlet.FilterChain) chain).doFilter(
                    (jakarta.servlet.ServletRequest) request,
                    (jakarta.servlet.ServletResponse) response
            );
            return null;
        }).when(slackSignatureVerificationFilter).doFilter(any(), any(), any());

        Map<String, String> request = Map.of(
                "type", "url_verification",
                "challenge", "test_challenge_string"
        );

        // when & then
        mockMvc.perform(post("/api/v1/slack/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").value("test_challenge_string"));
    }

    @Test
    @DisplayName("event_callback 수신 시 성공 응답을 반환한다")
    void eventCallbackTest() throws Exception {
        // given
        doAnswer(invocation -> {
            var request = invocation.getArgument(0);
            var response = invocation.getArgument(1);
            var chain = invocation.getArgument(2);
            ((jakarta.servlet.FilterChain) chain).doFilter(
                    (jakarta.servlet.ServletRequest) request,
                    (jakarta.servlet.ServletResponse) response
            );
            return null;
        }).when(slackSignatureVerificationFilter).doFilter(any(), any(), any());

        Map<String, Object> request = Map.of(
                "type", "event_callback",
                "team_id", "T12345",
                "event_id", "Ev99999",
                "event", Map.of(
                        "type", "app_mention",
                        "channel", "C12345",
                        "text", "안녕 AI Office!"
                )
        );

        // when & then
        mockMvc.perform(post("/api/v1/slack/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("성공"));
    }
}