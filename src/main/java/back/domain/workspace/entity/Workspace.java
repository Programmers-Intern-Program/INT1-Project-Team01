package back.domain.workspace.entity;

import java.time.LocalDateTime;

import back.domain.member.entity.Member;
import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "CT_CONSTRUCTOR_THROW"},
        justification = "JPA 연관 엔티티 반환은 Spring 컨텍스트에서 관리되며, 생성자 예외는 도메인 불변식 보호를 위한 의도적 설계임")
@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Workspace extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private Member createdByMember;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Workspace(String name, String description, Member createdByMember) {
        this.name = requireNotBlank(name, "name");
        this.description = normalizeDescription(description);
        this.createdByMember = requireMember(createdByMember);
    }

    public static Workspace create(String name, String description, Member createdByMember) {
        return new Workspace(name, description, createdByMember);
    }

    public void update(String name, String description) {
        if (name != null) {
            this.name = requireNotBlank(name, "name");
        }
        this.description = normalizeDescription(description);
    }

    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    private static Member requireMember(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("createdByMember must not be null");
        }
        return member;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
