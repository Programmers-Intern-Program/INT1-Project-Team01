package back.domain.slack.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Slack Events API로부터 수신되는 웹훅 페이로드를 매핑하는 DTO입니다.
 * URL 검증(url_verification)과 일반 이벤트(event_callback)를 모두 수용하도록 설계되었습니다.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackEventReq(
        String type,
        String challenge,
        @JsonProperty("team_id") String teamId,
        @JsonProperty("event_id") String eventId,
        SlackEventDetail event
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlackEventDetail(
            String type,
            String channel,
            String user,
            String text,
            String ts
    ) {}
}