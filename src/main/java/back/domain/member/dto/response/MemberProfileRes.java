package back.domain.member.dto.response;

public record MemberProfileRes(
        Long memberId,
        String displayName,
        String avatarKind,
        MemberAvatarColorsRes avatarColors
) {
}
