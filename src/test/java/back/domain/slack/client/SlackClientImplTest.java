package back.domain.slack.client;

import back.domain.slack.dto.request.SlackMessageReq;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}