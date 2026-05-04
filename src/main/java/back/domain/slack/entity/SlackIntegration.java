package back.domain.slack.entity;

import back.global.jpa.entity.BaseEntity;
import back.global.security.crypto.TinkCryptoConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Slack 채널과 Workspace 간의 연동 정보를 저장하는 도메인 엔티티입니다.
 * <p>
 * 사용자가 수동으로 등록한 Slack Bot Token 및 Signing Secret을 보관합니다.
 * 보안 요구사항에 따라 중요 토큰 정보는 {@code TinkCryptoConverter}를 통해 데이터베이스에 암호화되어 저장되며,
 * 애플리케이션 내에서는 평문으로 복호화되어 다루어집니다.
 *
 * <p><b>상속 정보:</b><br>
 * {@code BaseEntity}를 상속받아 {@code id}, {@code createdAt}, {@code updatedAt} 필드를 기본으로 가집니다.
 *
 * <p><b>주요 생성자:</b><br>
 * {@code SlackIntegration(...)} <br>
 * 롬복의 {@code @Builder}를 통해 생성되며, 연동에 필요한 필수 정보(Workspace ID, Slack Team ID, Slack Channel ID,
 * Bot Token, Signing Secret, 등록자 ID)를 매개변수로 받습니다. <br>
 *
 * @author minhee
 * @see back.global.security.crypto.TinkCryptoConverter
 * @since 2026-04-30
 */
@Entity
@Table(name = "slack_integrations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"slack_team_id", "slack_channel_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackIntegration extends BaseEntity {

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 100)
    private String slackTeamId;

    @Column(nullable = false, length = 100)
    private String slackChannelId;

    @Convert(converter = TinkCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT", name = "bot_token_encrypted")
    private String botToken;

    @Convert(converter = TinkCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT", name = "signing_secret_encrypted")
    private String signingSecret;

    @Column(nullable = false)
    private Long createdByMemberId;

    @Builder
    public SlackIntegration(Long workspaceId, String slackTeamId, String slackChannelId,
                            String botToken, String signingSecret, Long createdByMemberId) {
        this.workspaceId = workspaceId;
        this.slackTeamId = slackTeamId;
        this.slackChannelId = slackChannelId;
        this.botToken = botToken;
        this.signingSecret = signingSecret;
        this.createdByMemberId = createdByMemberId;
    }
}