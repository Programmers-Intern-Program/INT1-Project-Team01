package back.domain.workspace.entity;

import java.time.LocalDateTime;

import back.domain.member.entity.Member;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "CT_CONSTRUCTOR_THROW"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리되며, 생성자 예외는 도메인 불변식 보호를 위한 의도적 설계임")
@Getter
@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "member_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private WorkspaceMemberRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private WorkspaceMember(Workspace workspace, Member member, WorkspaceMemberRole role) {
        this.workspace = requireWorkspace(workspace);
        this.member = requireMember(member);
        this.role = requireRole(role);
        this.joinedAt = LocalDateTime.now();
    }

    public static WorkspaceMember create(Workspace workspace, Member member, WorkspaceMemberRole role) {
        return new WorkspaceMember(workspace, member, role);
    }

    public void changeRole(WorkspaceMemberRole role) {
        this.role = requireRole(role);
    }

    private static Workspace requireWorkspace(Workspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        return workspace;
    }

    private static Member requireMember(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member must not be null");
        }
        return member;
    }

    private static WorkspaceMemberRole requireRole(WorkspaceMemberRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return role;
    }
}
