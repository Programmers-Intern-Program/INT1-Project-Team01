package back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.member.dto.request.MemberAvatarColorsReq;
import back.domain.member.dto.request.MemberProfileUpdateReq;
import back.domain.member.dto.response.MemberProfileRes;
import back.domain.member.entity.Member;
import back.domain.member.entity.MemberProfile;
import back.domain.member.repository.MemberProfileRepository;
import back.domain.member.repository.MemberRepository;
import back.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceImplTest {
    @Mock private MemberRepository memberRepository;
    @Mock private MemberProfileRepository memberProfileRepository;

    private MemberProfileServiceImpl memberProfileService;
    private Member member;

    @BeforeEach
    void setUp() {
        memberProfileService = new MemberProfileServiceImpl(memberRepository, memberProfileRepository);
        member = Member.createUser("google-sub", "test@test.com", "홍길동");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Test
    @DisplayName("내 프로필 조회 시 프로필이 없으면 기본 프로필을 생성한다")
    void getMyProfile_createsDefaultProfile() {
        // given
        MemberProfile defaultProfile = MemberProfile.createDefault(member);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(memberProfileRepository.findByMemberId(1L)).willReturn(Optional.empty());
        given(memberProfileRepository.save(any(MemberProfile.class))).willReturn(defaultProfile);

        // when
        MemberProfileRes result = memberProfileService.getMyProfile(1L);

        // then
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.displayName()).isEqualTo("홍길동");
        assertThat(result.avatarKind()).isEqualTo("mira");
        assertThat(result.avatarColors().skin()).isEqualTo("#f8d4b0");
    }

    @Test
    @DisplayName("내 프로필 수정 성공")
    void updateMyProfile_success() {
        // given
        MemberProfile profile = MemberProfile.createDefault(member);
        MemberProfileUpdateReq request = new MemberProfileUpdateReq(
                "길동",
                "mira",
                new MemberAvatarColorsReq("#111111", "#222222", "#333333"));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(memberProfileRepository.findByMemberId(1L)).willReturn(Optional.of(profile));

        // when
        MemberProfileRes result = memberProfileService.updateMyProfile(1L, request);

        // then
        assertThat(result.displayName()).isEqualTo("길동");
        assertThat(result.avatarColors().skin()).isEqualTo("#111111");
        assertThat(result.avatarColors().hair()).isEqualTo("#222222");
        assertThat(result.avatarColors().shirt()).isEqualTo("#333333");
    }

    @Test
    @DisplayName("내 프로필 수정 실패 - 색상 형식 오류")
    void updateMyProfile_invalidColor_throwsException() {
        // given
        MemberProfile profile = MemberProfile.createDefault(member);
        MemberProfileUpdateReq request = new MemberProfileUpdateReq(
                "길동",
                "mira",
                new MemberAvatarColorsReq("red", null, null));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(memberProfileRepository.findByMemberId(1L)).willReturn(Optional.of(profile));

        // when & then
        assertThatThrownBy(() -> memberProfileService.updateMyProfile(1L, request))
                .isInstanceOf(ServiceException.class);
    }
}
