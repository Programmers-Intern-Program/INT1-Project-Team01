package back.domain.slack.client;

import back.domain.slack.dto.request.SlackMessageReq;
import back.domain.slack.dto.response.SlackOAuthAccessRes;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class SlackClientImplTest {

    private SlackClientImpl slackClient;
    private MockRestServiceServer mockServer;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        slackClient = new SlackClientImpl(builder.build());
    }

    // --- 기존 sendMessage 테스트 (유지) ---

    @Test
    @DisplayName("정상 응답 시 예외 없이 전송에 성공한다")
    void sendMessage_Success() throws Exception {
        // given
        SlackMessageReq req = SlackMessageReq.builder()
                .channel("C12345")
                .text("테스트 메시지")
                .threadTs("12345.678")
                .build();
        String responseBody = "{\"ok\":true}";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer valid-token"))
                .andExpect(content().json(jsonMapper.writeValueAsString(req)))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatCode(() -> slackClient.sendMessage("valid-token", req))
                .doesNotThrowAnyException();
        mockServer.verify();
    }

    @Test
    @DisplayName("Slack API 논리적 오류(ok:false) 시 ServiceException을 던진다")
    void sendMessage_LogicalError_ThrowsServiceException() {
        // given
        SlackMessageReq req = SlackMessageReq.builder()
                .channel("C12345")
                .text("테스트 메시지")
                .build();
        String responseBody = "{\"ok\":false, \"error\":\"invalid_auth\"}";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> slackClient.sendMessage("invalid-token", req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Slack API logical error. error: invalid_auth");
        mockServer.verify();
    }

    @Test
    @DisplayName("네트워크 장애(500) 시 ServiceException을 던진다")
    void sendMessage_NetworkError_ThrowsServiceException() {
        // given
        SlackMessageReq req = SlackMessageReq.builder().channel("C12345").build();

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> slackClient.sendMessage("token", req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Slack API network error");
        mockServer.verify();
    }

    @Test
    @DisplayName("OAuth 토큰 교환 정상 응답 시 SlackOAuthAccessRes를 파싱하여 반환한다")
    void exchangeToken_Success() {
        // given
        String code = "test-code";
        String clientId = "test-client";
        String clientSecret = "test-secret";
        String redirectUri = "http://localhost/callback";

        String responseBody = """
                {
                    "ok": true,
                    "access_token": "xoxb-123456",
                    "team": {
                        "id": "T12345",
                        "name": "Test Team"
                    },
                    "incoming_webhook": {
                        "channel_id": "C99999",
                        "url": "https://hooks.slack.com/services/..."
                    }
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/oauth.v2.access"))
                .andExpect(method(HttpMethod.POST))
                // Form Data 전송 확인
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("code=test-code")))
                .andExpect(content().string(containsString("client_id=test-client")))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        SlackOAuthAccessRes res = slackClient.exchangeToken(code, clientId, clientSecret, redirectUri);

        // then
        assertThat(res).isNotNull();
        assertThat(res.ok()).isTrue();
        assertThat(res.accessToken()).isEqualTo("xoxb-123456");
        assertThat(res.team().id()).isEqualTo("T12345");
        assertThat(res.incomingWebhook().channelId()).isEqualTo("C99999");

        mockServer.verify();
    }

    @Test
    @DisplayName("OAuth 토큰 교환 시 논리적 오류(ok:false)가 발생하면 ServiceException을 던진다")
    void exchangeToken_LogicalError_ThrowsServiceException() {
        // given
        String responseBody = "{\"ok\":false, \"error\":\"invalid_client_id\"}";

        mockServer.expect(requestTo("https://slack.com/api/oauth.v2.access"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> slackClient.exchangeToken("code", "id", "secret", null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Slack OAuth logical error: invalid_client_id");

        mockServer.verify();
    }

    @Test
    @DisplayName("OAuth 토큰 교환 중 네트워크 장애 발생 시 ServiceException을 던진다")
    void exchangeToken_NetworkError_ThrowsServiceException() {
        // given
        mockServer.expect(requestTo("https://slack.com/api/oauth.v2.access"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> slackClient.exchangeToken("code", "id", "secret", "uri"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Slack API network error");

        mockServer.verify();
    }
}