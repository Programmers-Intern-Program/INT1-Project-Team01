package back.domain.slack.entity;

import back.domain.workspace.entity.Workspace;
import back.global.jpa.entity.BaseEntity;
import back.global.security.crypto.TinkCryptoConverter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.*;
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
 * @author minhee
 * @see back.global.security.crypto.TinkCryptoConverter
 * @since 2026-04-30
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리됨")
@Entity
@Table(name = "slack_integrations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"slack_team_id", "slack_channel_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackIntegration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

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
    public SlackIntegration(Workspace workspace, String slackTeamId, String slackChannelId,
                            String botToken, String signingSecret, Long createdByMemberId) {
        this.workspace = workspace;
        this.slackTeamId = slackTeamId;
        this.slackChannelId = slackChannelId;
        this.botToken = botToken;
        this.signingSecret = signingSecret;
        this.createdByMemberId = createdByMemberId;
    }
}