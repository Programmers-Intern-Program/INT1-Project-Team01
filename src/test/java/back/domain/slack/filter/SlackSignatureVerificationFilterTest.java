package back.domain.slack.filter;

import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link SlackSignatureVerificationFilter} 단독 테스트.
 * <p>
 * Spring 컨텍스트 없이 필터 인스턴스를 직접 생성하여
 * 서명 검증, 타임스탬프 검증, Replay Attack 방지 로직을 검증합니다.
 *
 * @author minhee
 * @since 2026-05-04
 */
@ExtendWith(MockitoExtension.class)
class SlackSignatureVerificationFilterTest {

    @Mock
    private SlackIntegrationRepository slackIntegrationRepository;

    private SlackSignatureVerificationFilter filter;
    private JsonMapper jsonMapper;

    private static final String PLAIN_SIGNING_SECRET = "test-signing-secret";
    private static final String SLACK_WEBHOOK_URI = "/api/v1/slack/events";

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        filter = new SlackSignatureVerificationFilter(
                jsonMapper,
                slackIntegrationRepository,
                300
        );
    }

    // 유효한 HMAC-SHA256 서명 계산 헬퍼
    private String calculateSignature(String timestamp, String body) throws Exception {
        String baseString = "v0:" + timestamp + ":" + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                PLAIN_SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
        return "v0=" + HexFormat.of().formatHex(hash);
    }

    private MockHttpServletRequest buildRequest(String timestamp, String signature, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(SLACK_WEBHOOK_URI);
        request.setMethod("POST");
        request.addHeader("X-Slack-Request-Timestamp", timestamp);
        request.addHeader("X-Slack-Signature", signature);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    @DisplayName("유효한 서명이면 필터 체인을 통과하고 body를 다시 읽을 수 있다")
    void validSignature_passesFilterChain() throws Exception {
        // given
        String body = "{\"team_id\":\"T12345\",\"type\":\"event_callback\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        String signature = calculateSignature(timestamp, body);

        SlackIntegration integration = SlackIntegration.builder()
                .signingSecret(PLAIN_SIGNING_SECRET)
                .build();

        given(slackIntegrationRepository.findFirstBySlackTeamId("T12345"))
                .willReturn(Optional.of(integration));

        MockHttpServletRequest request = buildRequest(timestamp, signature, body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        HttpServlet mockServlet = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(HttpServletResponse.SC_OK);
            }
        };

        MockFilterChain chain = new MockFilterChain(
                mockServlet,
                (req, res, filterChain) -> {
                    byte[] bytes = StreamUtils.copyToByteArray(req.getInputStream());
                    assertThat(bytes).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
                    filterChain.doFilter(req, res);
                }
        );

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("서명 헤더가 누락되면 401을 반환한다")
    void missingSignatureHeader_returns401() throws Exception {
        // given
        String body = "{\"team_id\":\"T12345\",\"type\":\"event_callback\"}";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(SLACK_WEBHOOK_URI);
        request.addHeader("X-Slack-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
        // X-Slack-Signature 헤더 누락
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("타임스탬프가 만료되면 401을 반환한다 (Replay Attack)")
    void expiredTimestamp_returns401() throws Exception {
        // given
        String body = "{\"team_id\":\"T12345\",\"type\":\"event_callback\"}";
        // 301초 전 타임스탬프 (임계값 300초 초과)
        String expiredTimestamp = String.valueOf(Instant.now().getEpochSecond() - 301);
        String signature = calculateSignature(expiredTimestamp, body);

        MockHttpServletRequest request = buildRequest(expiredTimestamp, signature, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("타임스탬프 형식이 잘못되면 400을 반환한다")
    void invalidTimestampFormat_returns400() throws Exception {
        // given
        String body = "{\"team_id\":\"T12345\",\"type\":\"event_callback\"}";
        MockHttpServletRequest request = buildRequest("not-a-number", "v0=fakesig", body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("team_id가 없는 페이로드면 400을 반환한다")
    void missingTeamId_returns400() throws Exception {
        // given
        String body = "{\"type\":\"event_callback\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = calculateSignature(timestamp, body);

        MockHttpServletRequest request = buildRequest(timestamp, signature, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("등록되지 않은 team_id면 404를 반환한다")
    void unregisteredTeamId_returns404() throws Exception {
        // given
        String body = "{\"team_id\":\"UNKNOWN\",\"type\":\"event_callback\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = calculateSignature(timestamp, body);

        given(slackIntegrationRepository.findFirstBySlackTeamId("UNKNOWN"))
                .willReturn(Optional.empty());

        MockHttpServletRequest request = buildRequest(timestamp, signature, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("서명이 불일치하면 401을 반환한다")
    void signatureMismatch_returns401() throws Exception {
        // given
        String body = "{\"team_id\":\"T12345\",\"type\":\"event_callback\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        SlackIntegration integration = SlackIntegration.builder()
                .signingSecret("PLAIN_SIGNING_SECRET")
                .build();

        given(slackIntegrationRepository.findFirstBySlackTeamId("T12345"))
                .willReturn(Optional.of(integration));

        MockHttpServletRequest request = buildRequest(timestamp, "v0=invalidsignature", body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Slack 경로가 아니면 필터를 건너뛴다")
    void nonSlackUri_skipsFilter() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
    }
}