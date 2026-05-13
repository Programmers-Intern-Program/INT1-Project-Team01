package back.domain.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import back.domain.member.controller.docs.MemberControllerDocs;
import back.domain.member.dto.request.MemberProfileUpdateReq;
import back.domain.member.dto.response.MemberProfileRes;
import back.domain.member.service.MemberProfileService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/members")
@Validated
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {
    private final MemberProfileService memberProfileService;

    @Override
    @GetMapping("/me/profile")
    public ResponseEntity<RsData<MemberProfileRes>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(new RsData<>(
                memberProfileService.getMyProfile(memberId),
                "멤버 프로필 조회 성공"));
    }

    @Override
    @PatchMapping("/me/profile")
    public ResponseEntity<RsData<MemberProfileRes>> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody MemberProfileUpdateReq request) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(new RsData<>(
                memberProfileService.updateMyProfile(memberId, request),
                "멤버 프로필 수정 성공"));
    }

    private long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[MemberController#resolveAuthenticatedMemberId] authenticated member is missing",
                    CommonErrorCode.UNAUTHORIZED.defaultMessage());
        }

        return authenticatedMember.memberId();
    }
}
