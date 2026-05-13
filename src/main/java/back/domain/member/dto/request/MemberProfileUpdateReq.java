package back.domain.member.dto.request;

public record MemberProfileUpdateReq(
        String displayName,
        String avatarKind,
        MemberAvatarColorsReq avatarColors
) {
}
