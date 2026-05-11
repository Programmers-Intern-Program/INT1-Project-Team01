package back.domain.slack.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Slack OAuth 2.0 토큰 교환 API의 응답 데이터를 매핑하는 DTO입니다.
 */
public record SlackOAuthAccessRes(
        boolean ok,
        @JsonProperty("access_token") String accessToken,
        Team team,
        @JsonProperty("incoming_webhook") IncomingWebhook incomingWebhook,
        String error
) {
    /**
     * Slack 워크스페이스(팀) 정보
     */
    public record Team(
            String id,
            String name
    ) {}

    /**
     * 선택된 채널의 Incoming Webhook 정보
     */
    public record IncomingWebhook(
            @JsonProperty("channel_id") String channelId,
            String url
    ) {}
}