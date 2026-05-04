package back.domain.workspace.entity;

import java.time.LocalDateTime;

import back.domain.member.entity.Member;
import back.domain.workspace.enums.InviteEmailStatus;
import back.domain.workspace.enums.WorkspaceInviteStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;
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
@Table(name = "workspace_invites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "token", nullable = false, unique = true, length = 100)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private WorkspaceMemberRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private Member createdByMember;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "target_email", length = 320)
    private String targetEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private InviteEmailStatus emailStatus = InviteEmailStatus.NOT_REQUESTED;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    private LocalDateTime acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_member_id")
    private Member acceptedByMember;

    private LocalDateTime revokedAt;

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    // ====== 생성자 ======

    private WorkspaceInvite(
            Workspace workspace,
            String token,
            WorkspaceMemberRole role,
            Member createdByMember,
            LocalDateTime expiresAt) {
        this.workspace = requireWorkspace(workspace);
        this.token = requireToken(token);
        this.role = requireRole(role);
        this.createdByMember = requireMember(createdByMember, "createdByMember");
        this.expiresAt = requireExpiresAt(expiresAt);
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return token.trim();
    }

    private static WorkspaceMemberRole requireRole(WorkspaceMemberRole role) {
        if (role == null) {
            return WorkspaceMemberRole.MEMBER;
        }
        return role;
    }

    private static Member requireMember(Member member, String fieldName) {
        if (member == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return member;
    }

    private static LocalDateTime requireExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        return expiresAt;
    }

    private static Workspace requireWorkspace(Workspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        return workspace;
    }

    // ====== Workspace Invite(링크) 생성 ======

    public static WorkspaceInvite create(
            Workspace workspace,
            String token,
            WorkspaceMemberRole role,
            Member createdByMember,
            LocalDateTime expiresAt) {
        return new WorkspaceInvite(workspace, token, role, createdByMember, expiresAt);
    }

    // ====== Workspace Invite(링크) 상태 ======

    public WorkspaceInviteStatus getStatus(LocalDateTime now) {
        if (revokedAt != null) {
            return WorkspaceInviteStatus.REVOKED;
        }
        if (acceptedAt != null) {
            return WorkspaceInviteStatus.ACCEPTED;
        }
        if (expiresAt.isBefore(now)) {
            return WorkspaceInviteStatus.EXPIRED;
        }
        return WorkspaceInviteStatus.PENDING;
    }

    // ====== Email ======

    public boolean hasTargetEmail() {
        return targetEmail != null && !targetEmail.isBlank();
    }

    public void requestEmailDelivery(String targetEmail) {
        if (targetEmail == null || targetEmail.isBlank()) {
            this.targetEmail = null;
            this.emailStatus = InviteEmailStatus.NOT_REQUESTED;
            this.emailSentAt = null;
            return;
        }

        this.targetEmail = targetEmail.trim();
        this.emailStatus = InviteEmailStatus.PENDING;
        this.emailSentAt = null;
    }

    public void markEmailSending() {
        this.emailStatus = InviteEmailStatus.PENDING;
    }

    public void markEmailSent() {
        this.emailStatus = InviteEmailStatus.SENT;
        this.emailSentAt = LocalDateTime.now();
    }

    public void markEmailFailed(String reason) {
        this.emailStatus = InviteEmailStatus.FAILED;
    }

    public void accept(Member acceptedByMember) {
        this.acceptedByMember = requireMember(acceptedByMember, "acceptedByMember");
        this.acceptedAt = LocalDateTime.now();
    }
}
