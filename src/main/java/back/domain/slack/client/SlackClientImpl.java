package back.domain.slack.client;

import back.domain.slack.dto.request.SlackMessageReq;
import back.domain.slack.dto.response.SlackMessageRes;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "RestClient는 Spring이 관리하는 스레드 안전 객체이며 불변으로 사용되므로 내부 표현 노출 위험이 없음"
)
public class SlackClientImpl implements SlackClient {

    private final RestClient restClient;
    private static final String SLACK_POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage";

    @Override
    public void sendMessage(String botToken, SlackMessageReq request) {
        log.debug("Slack 메시지 전송 시도. channel: {}", request.channel());

        try {
            SlackMessageRes response = restClient.post()
                    .uri(SLACK_POST_MESSAGE_URL)
                    .header("Authorization", "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SlackMessageRes.class);

            if (response == null || !response.ok()) {
                String errorMessage = response != null ? response.error() : "Unknown error";
                throw new ServiceException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR,
                        "[SlackClientImpl#sendMessage] Slack API logical error. error: " + errorMessage,
                        "Slack 메시지 전송에 실패했습니다."
                );
            }

            log.info("Slack 메시지 전송 성공. channel: {}", request.channel());

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Slack 메시지 전송 중 네트워크 오류 발생. channel: {}, error: {}",
                    request.channel(), e.getMessage(), e);
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[SlackClientImpl#sendMessage] Slack API network error: " + e.getMessage(),
                    "Slack 시스템과 통신하는 중 문제가 발생했습니다."
            );
        }
    }
}