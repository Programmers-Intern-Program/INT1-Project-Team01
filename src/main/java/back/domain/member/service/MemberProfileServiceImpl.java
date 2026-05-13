package back.domain.member.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.member.dto.request.MemberAvatarColorsReq;
import back.domain.member.dto.request.MemberProfileUpdateReq;
import back.domain.member.dto.response.MemberProfileRes;
import back.domain.member.entity.Member;
import back.domain.member.entity.MemberProfile;
import back.domain.member.repository.MemberProfileRepository;
import back.domain.member.repository.MemberRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberProfileServiceImpl implements MemberProfileService {
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_AVATAR_KIND_LENGTH = 50;

    private final MemberRepository memberRepository;
    private final MemberProfileRepository memberProfileRepository;

    @Override
    @Transactional
    public MemberProfileRes getMyProfile(long memberId) {
        Member member = getMemberOrThrow(memberId);
        MemberProfile profile = getOrCreateProfile(member);
        return MemberProfileMapper.toResponse(profile);
    }

    @Override
    @Transactional
    public MemberProfileRes updateMyProfile(long memberId, MemberProfileUpdateReq request) {
        if (request == null) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[MemberProfileServiceImpl#updateMyProfile] request body is missing",
                    "프로필 수정 요청 본문이 필요합니다.");
        }
        Member member = getMemberOrThrow(memberId);
        MemberProfile profile = getOrCreateProfile(member);
        String displayName = resolveText(
                request.displayName(), profile.getDisplayName(), "displayName", MAX_DISPLAY_NAME_LENGTH);
        String avatarKind = resolveText(
                request.avatarKind(), profile.getAvatarKind(), "avatarKind", MAX_AVATAR_KIND_LENGTH);
        String skinColor = resolveColor(request.avatarColors(), AvatarColorPart.SKIN, profile.getAvatarSkinColor());
        String hairColor = resolveColor(request.avatarColors(), AvatarColorPart.HAIR, profile.getAvatarHairColor());
        String shirtColor = resolveColor(request.avatarColors(), AvatarColorPart.SHIRT, profile.getAvatarShirtColor());

        profile.update(displayName, avatarKind, skinColor, hairColor, shirtColor);
        return MemberProfileMapper.toResponse(profile);
    }

    private MemberProfile getOrCreateProfile(Member member) {
        return memberProfileRepository.findByMemberId(member.getId())
                .orElseGet(() -> memberProfileRepository.save(MemberProfile.createDefault(member)));
    }

    private Member getMemberOrThrow(long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[MemberProfileServiceImpl#getMemberOrThrow] member not found. memberId=" + memberId,
                        "회원을 찾을 수 없습니다."));
    }

    private String resolveText(String nextValue, String currentValue, String fieldName, int maxLength) {
        if (nextValue == null) {
            return currentValue;
        }
        if (nextValue.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[MemberProfileServiceImpl#resolveText] profile text is blank. field=" + fieldName,
                    fieldName + "은(는) 비어 있을 수 없습니다.");
        }
        String trimmed = nextValue.trim();
        if (trimmed.length() > maxLength) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[MemberProfileServiceImpl#resolveText] profile text is too long. field=" + fieldName,
                    fieldName + "은(는) " + maxLength + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    private String resolveColor(MemberAvatarColorsReq colors, AvatarColorPart part, String currentValue) {
        if (colors == null) {
            return currentValue;
        }

        String nextValue = switch (part) {
            case SKIN -> colors.skin();
            case HAIR -> colors.hair();
            case SHIRT -> colors.shirt();
        };
        if (nextValue == null) {
            return currentValue;
        }
        if (!HEX_COLOR_PATTERN.matcher(nextValue).matches()) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[MemberProfileServiceImpl#resolveColor] invalid avatar color. part=" + part,
                    "아바타 색상은 #RRGGBB 형식이어야 합니다.");
        }
        return nextValue;
    }

    private enum AvatarColorPart {
        SKIN,
        HAIR,
        SHIRT
    }
}
