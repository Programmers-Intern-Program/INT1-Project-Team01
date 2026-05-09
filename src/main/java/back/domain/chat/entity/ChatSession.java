package back.domain.chat.entity;

import java.time.LocalDateTime;

import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "JPA entity constructors validate domain invariants and this entity has no finalizer.")
@Getter
@Entity
@Table(
        name = "chat_sessions",
        indexes = {
            @Index(name = "idx_chat_sessions_workspace_agent_status", columnList = "workspace_id, agent_id, status")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_chat_sessions_workspace_source_ref",
                    columnNames = {"workspace_id", "source", "source_ref"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseEntity {

    private static final int SOURCE_REF_MAX_LENGTH = 255;
    private static final int OPEN_CLAW_SESSION_KEY_MAX_LENGTH = 220;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatSessionSource source;

    @Column(name = "source_ref", length = SOURCE_REF_MAX_LENGTH)
    private String sourceRef;

    @Column(name = "open_claw_session_key", nullable = false, length = OPEN_CLAW_SESSION_KEY_MAX_LENGTH)
    private String openClawSessionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatSessionStatus status;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    private ChatSession(
            Long workspaceId, Long agentId, ChatSessionSource source, String sourceRef, String openClawSessionKey) {
        this.workspaceId = requireId(workspaceId, "workspaceId");
        this.agentId = requireId(agentId, "agentId");
        this.source = requireSource(source);
        this.sourceRef = normalizeSourceRef(this.source, sourceRef);
        this.openClawSessionKey =
                requireNotBlank(openClawSessionKey, "openClawSessionKey", OPEN_CLAW_SESSION_KEY_MAX_LENGTH);
        this.status = ChatSessionStatus.ACTIVE;
        this.lastMessageAt = LocalDateTime.now();
    }

    public static ChatSession start(
            Long workspaceId, Long agentId, ChatSessionSource source, String sourceRef, String openClawSessionKey) {
        return new ChatSession(workspaceId, agentId, source, sourceRef, openClawSessionKey);
    }

    public void recordMessage() {
        this.lastMessageAt = LocalDateTime.now();
    }

    public void close() {
        this.status = ChatSessionStatus.CLOSED;
    }

    public void reopen() {
        this.status = ChatSessionStatus.ACTIVE;
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static ChatSessionSource requireSource(ChatSessionSource value) {
        if (value == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return value;
    }

    private static String requireNotBlank(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizeRequired(value, fieldName, maxLength);
    }

    private static String normalizeOptional(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeRequired(value, fieldName, maxLength);
    }

    private static String normalizeSourceRef(ChatSessionSource source, String sourceRef) {
        if (source == ChatSessionSource.SLACK) {
            return requireNotBlank(sourceRef, "sourceRef", SOURCE_REF_MAX_LENGTH);
        }
        return normalizeOptional(sourceRef, "sourceRef", SOURCE_REF_MAX_LENGTH);
    }

    private static String normalizeRequired(String value, String fieldName, int maxLength) {
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be less than or equal to " + maxLength);
        }
        return trimmed;
    }
}
