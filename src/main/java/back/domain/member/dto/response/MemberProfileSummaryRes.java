package back.domain.member.dto.response;

public record MemberProfileSummaryRes(
        String displayName,
        String avatarKind,
        MemberAvatarColorsRes avatarColors
) {
}
