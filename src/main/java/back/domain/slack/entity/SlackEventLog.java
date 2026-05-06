package back.domain.slack.entity;

import back.domain.slack.enums.SlackEventProcessingStatus;
import back.global.jpa.entity.BaseEntity;
import back.global.security.crypto.TinkCryptoConverter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Slack 이벤트 수신 로그를 저장하는 엔티티입니다.
 * 중복 이벤트를 방지하고 각 이벤트의 처리 상태를 관리합니다.
 */

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "slack_event_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_slack_event_id", columnNames = {"slack_event_id"})
        }
)
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리되며 외부 변조 위험 없음")
public class SlackEventLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id")
    private SlackIntegration integration;

    @Column(name = "slack_event_id", nullable = false, length = 100, updatable = false)
    private String slackEventId;

    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    @Convert(converter = TinkCryptoConverter.class)
    @Column(name = "raw_payload", columnDefinition = "TEXT", updatable = false)
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private SlackEventProcessingStatus processingStatus;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    public void markAsProcessed() {
        this.processingStatus = SlackEventProcessingStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.processingStatus = SlackEventProcessingStatus.FAILED;
        this.error = errorMessage != null && errorMessage.length() > 5000
                ? errorMessage.substring(0, 5000) + "...(truncated)"
                : errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsIgnored(String reason) {
        this.processingStatus = SlackEventProcessingStatus.IGNORED;
        this.error = reason;
        this.processedAt = LocalDateTime.now();
    }

    public void updateIntegration(SlackIntegration integration) {
        this.integration = integration;
    }
}