package back.domain.slack.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Slack chat.postMessage API 요청을 위한 DTO입니다.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SlackMessageReq(
        @JsonProperty("channel") String channel,
        @JsonProperty("text") String text,
        @JsonProperty("thread_ts") String threadTs
) {
}