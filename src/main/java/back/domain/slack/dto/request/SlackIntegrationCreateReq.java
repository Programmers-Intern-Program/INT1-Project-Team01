package back.domain.slack.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SlackIntegrationCreateReq(
        @NotBlank(message = "Slack Team ID는 필수입니다.")
        String slackTeamId,

        @NotBlank(message = "Slack Channel ID는 필수입니다.")
        String slackChannelId,

        @NotBlank(message = "Bot Token은 필수입니다.")
        String botToken,

        @NotBlank(message = "Signing Secret은 필수입니다.")
        String signingSecret
) {
}