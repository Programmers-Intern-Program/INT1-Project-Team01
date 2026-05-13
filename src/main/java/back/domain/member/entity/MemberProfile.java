package back.domain.member.entity;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member_profiles",
        uniqueConstraints = @UniqueConstraint(columnNames = "member_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberProfile extends BaseEntity {
    public static final String DEFAULT_AVATAR_KIND = "mira";
    public static final String DEFAULT_SKIN_COLOR = "#f8d4b0";
    public static final String DEFAULT_HAIR_COLOR = "#1a1a1a";
    public static final String DEFAULT_SHIRT_COLOR = "#2a3a4a";

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_kind", nullable = false, length = 50)
    private String avatarKind;

    @Column(name = "avatar_skin_color", nullable = false, length = 7)
    private String avatarSkinColor;

    @Column(name = "avatar_hair_color", nullable = false, length = 7)
    private String avatarHairColor;

    @Column(name = "avatar_shirt_color", nullable = false, length = 7)
    private String avatarShirtColor;

    private MemberProfile(
            Long memberId,
            String displayName,
            String avatarKind,
            String avatarSkinColor,
            String avatarHairColor,
            String avatarShirtColor) {
        this.memberId = memberId;
        this.displayName = displayName;
        this.avatarKind = avatarKind;
        this.avatarSkinColor = avatarSkinColor;
        this.avatarHairColor = avatarHairColor;
        this.avatarShirtColor = avatarShirtColor;
    }

    public static MemberProfile createDefault(Member member) {
        return new MemberProfile(
                member.getId(),
                member.getName(),
                DEFAULT_AVATAR_KIND,
                DEFAULT_SKIN_COLOR,
                DEFAULT_HAIR_COLOR,
                DEFAULT_SHIRT_COLOR);
    }

    public void update(
            String displayName,
            String avatarKind,
            String avatarSkinColor,
            String avatarHairColor,
            String avatarShirtColor) {
        this.displayName = displayName;
        this.avatarKind = avatarKind;
        this.avatarSkinColor = avatarSkinColor;
        this.avatarHairColor = avatarHairColor;
        this.avatarShirtColor = avatarShirtColor;
    }
}
