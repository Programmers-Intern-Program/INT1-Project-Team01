package back.domain.slack.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Slack API 전송 후 반환되는 응답 결과를 담는 DTO입니다.
 */
public record SlackMessageRes(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("error") String error
) {
}