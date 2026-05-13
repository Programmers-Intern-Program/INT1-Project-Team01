package back.domain.member.service;

import back.domain.member.dto.response.MemberAvatarColorsRes;
import back.domain.member.dto.response.MemberProfileRes;
import back.domain.member.dto.response.MemberProfileSummaryRes;
import back.domain.member.entity.Member;
import back.domain.member.entity.MemberProfile;

public final class MemberProfileMapper {
    private MemberProfileMapper() {
    }

    public static MemberProfileRes toResponse(MemberProfile profile) {
        return new MemberProfileRes(
                profile.getMemberId(),
                profile.getDisplayName(),
                profile.getAvatarKind(),
                toAvatarColors(profile));
    }

    public static MemberProfileSummaryRes toSummary(Member member, MemberProfile profile) {
        if (profile == null) {
            return defaultSummary(member);
        }

        return new MemberProfileSummaryRes(
                profile.getDisplayName(),
                profile.getAvatarKind(),
                toAvatarColors(profile));
    }

    private static MemberProfileSummaryRes defaultSummary(Member member) {
        return new MemberProfileSummaryRes(
                member.getName(),
                MemberProfile.DEFAULT_AVATAR_KIND,
                new MemberAvatarColorsRes(
                        MemberProfile.DEFAULT_SKIN_COLOR,
                        MemberProfile.DEFAULT_HAIR_COLOR,
                        MemberProfile.DEFAULT_SHIRT_COLOR));
    }

    private static MemberAvatarColorsRes toAvatarColors(MemberProfile profile) {
        return new MemberAvatarColorsRes(
                profile.getAvatarSkinColor(),
                profile.getAvatarHairColor(),
                profile.getAvatarShirtColor());
    }
}
