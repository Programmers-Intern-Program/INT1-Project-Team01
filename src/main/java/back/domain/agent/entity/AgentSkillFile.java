package back.domain.agent.entity;

import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "CT_CONSTRUCTOR_THROW"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리되며, 생성자 예외는 도메인 불변식 보호를 위한 의도적 설계임")
@Getter
@Entity
@Table(name = "agent_skill_files")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSkillFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false, length = 120)
    private String fileName;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentSkillSyncStatus syncStatus;

    @Column(length = 1000)
    private String syncError;

    private AgentSkillFile(Agent agent, String fileName, String content) {
        this.agent = requireAgent(agent);
        this.fileName = requireNotBlank(fileName, "fileName");
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        this.content = content;
        this.syncStatus = AgentSkillSyncStatus.PENDING;
    }

    public static AgentSkillFile create(Agent agent, String fileName, String content) {
        return new AgentSkillFile(agent, fileName, content);
    }

    public void markSynced() {
        this.syncStatus = AgentSkillSyncStatus.SYNCED;
        this.syncError = null;
    }

    public void markFailed(String syncError) {
        this.syncStatus = AgentSkillSyncStatus.FAILED;
        this.syncError = normalizeError(syncError);
    }

    private static Agent requireAgent(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent must not be null");
        }
        return agent;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeError(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        String trimmed = error.trim();
        if (trimmed.length() <= 1000) {
            return trimmed;
        }
        return trimmed.substring(0, 1000);
    }
}
