package back.domain.workspace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import back.domain.workspace.controller.docs.WorkspaceInviteControllerDocs;
import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
import back.domain.workspace.service.WorkspaceService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
@RestController
@RequestMapping("/api/v1/invites")
@Validated
@RequiredArgsConstructor
public class WorkspaceInviteController implements WorkspaceInviteControllerDocs {
    private final WorkspaceService workspaceService;

    // 초대 조회
    @Override
    @GetMapping("/{token}")
    public ResponseEntity<RsData<WorkspaceInvitePreviewRes>> getInviteInfo(@PathVariable String token) {
        return ResponseEntity.ok(new RsData<>(workspaceService.getInviteInfo(token), "초대 정보 조회 성공"));
    }

    // 초대 수락
    @Override
    @PostMapping("/{token}/accept")
    public ResponseEntity<RsData<Void>> acceptInvite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable String token) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        workspaceService.acceptInvite(token, memberId);
        return ResponseEntity.ok(new RsData<>("초대 수락 성공"));
    }

    private long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[WorkspaceInviteController#resolveAuthenticatedMemberId] authenticated member is missing",
                    CommonErrorCode.UNAUTHORIZED.defaultMessage());
        }

        return authenticatedMember.memberId();
    }
}
