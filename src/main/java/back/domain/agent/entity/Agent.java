package back.domain.agent.entity;

import back.domain.workspace.entity.Workspace;
import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
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
@Table(name = "agents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 160)
    private String openClawAgentId;

    @Column(nullable = false, length = 512)
    private String workspacePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentStatus status;

    @Column(length = 1000)
    private String syncError;

    @Column(nullable = false)
    private Long createdByMemberId;

    private Agent(Workspace workspace, String name, String workspacePath, Long createdByMemberId) {
        this.workspace = requireWorkspace(workspace);
        this.name = requireNotBlank(name, "name");
        this.workspacePath = requireNotBlank(workspacePath, "workspacePath");
        this.createdByMemberId = requireMemberId(createdByMemberId);
        this.status = AgentStatus.CREATING;
    }

    public static Agent create(Workspace workspace, String name, String workspacePath, Long createdByMemberId) {
        return new Agent(workspace, name, workspacePath, createdByMemberId);
    }

    public void markOpenClawCreated(String openClawAgentId) {
        this.openClawAgentId = requireNotBlank(openClawAgentId, "openClawAgentId");
        this.syncError = null;
    }

    public void markReady() {
        this.status = AgentStatus.READY;
        this.syncError = null;
    }

    public void markSyncFailed(String syncError) {
        this.status = AgentStatus.SYNC_FAILED;
        this.syncError = normalizeError(syncError);
    }

    public void markError(String syncError) {
        this.status = AgentStatus.ERROR;
        this.syncError = normalizeError(syncError);
    }

    private static Workspace requireWorkspace(Workspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        return workspace;
    }

    private static Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }
        return memberId;
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
