package back.domain.member.entity;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Column(name = "google_sub", nullable = false, length = 100, unique = true)
    private String googleSub;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    private Member(String googleSub, String email, String name, MemberRole role) {
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public static Member createUser(String googleSub, String email, String name) {
        Member member = new Member(
                requireNotBlank(googleSub, "googleSub"),
                requireNotBlank(email, "email"),
                requireNotBlank(name, "name"),
                MemberRole.USER);
        return member;
    }

    public static Member createAdmin(String googleSub, String email, String name) {
        return new Member(
                requireNotBlank(googleSub, "googleSub"),
                requireNotBlank(email, "email"),
                requireNotBlank(name, "name"),
                MemberRole.ADMIN);
    }

    public void updateName(String name) {
        this.name = requireNotBlank(name, "name");
    }

    public void updateEmail(String email) {
        this.email = requireNotBlank(email, "email");
    }

    public void promoteToAdmin() {
        this.role = MemberRole.ADMIN;
    }

    public boolean isAdmin() {
        return this.role == MemberRole.ADMIN;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
