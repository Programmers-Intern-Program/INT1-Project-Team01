package back.domain.slack.dto.response;

import back.domain.slack.entity.SlackIntegration;

public record SlackIntegrationInfoRes(
        Long id,
        String slackTeamId,
        String slackChannelId,
        String maskedBotToken
) {
    private static final int MIN_TOKEN_LENGTH_FOR_MASKING = 8;
    private static final int VISIBLE_PREFIX_LENGTH = 5; // 예: xoxb-
    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final String MASK_STRING = "****";

    public static SlackIntegrationInfoRes from(SlackIntegration entity) {
        return new SlackIntegrationInfoRes(
                entity.getId(),
                entity.getSlackTeamId(),
                entity.getSlackChannelId(),
                maskToken(entity.getBotToken())
        );
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= MIN_TOKEN_LENGTH_FOR_MASKING) {
            return MASK_STRING;
        }
        return token.substring(0, VISIBLE_PREFIX_LENGTH) +
                MASK_STRING +
                token.substring(token.length() - VISIBLE_SUFFIX_LENGTH);
    }
}