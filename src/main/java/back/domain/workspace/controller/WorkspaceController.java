package back.domain.workspace.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import back.domain.workspace.controller.docs.WorkspaceControllerDocs;
import back.domain.workspace.dto.request.CreateWorkspaceInviteReq;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.ExtendWorkspaceInviteReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.response.WorkspaceInviteInfoRes;
import back.domain.workspace.dto.response.WorkspaceInviteManagementRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
import back.domain.workspace.service.WorkspaceService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
@RestController
@RequestMapping("/api/v1/workspaces")
@Validated
@RequiredArgsConstructor
public class WorkspaceController implements WorkspaceControllerDocs {
    private final WorkspaceService workspaceService;

    // Workspace 생성
    @Override
    @PostMapping
    public ResponseEntity<RsData<WorkspaceInfoRes>> create(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody CreateWorkspaceReq request) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        WorkspaceInfoRes response = workspaceService.create(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RsData<>(
                        response,
                "워크스페이스가 생성되었습니다."
                )
        );
    }

    // Workspace 다건(목록) 조회
    @Override
    @GetMapping
    public ResponseEntity<RsData<List<WorkspaceSummaryInfoRes>>> listMine(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(
                new RsData<>(
                        workspaceService.listMyWorkspaces(memberId),
                        "워크스페이스 목록 조회 성공"
                )
        );
    }

    // Workspace 단건(상세) 조회
    @Override
    @GetMapping("/{workspaceId}")
    public ResponseEntity<RsData<WorkspaceInfoRes>> getWorkspace(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember, @PathVariable long workspaceId) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(
                new RsData<>(
                        workspaceService.getWorkspace(workspaceId, memberId),
                        "워크스페이스 조회 성공"
                )
        );
    }

    // Workspace 수정 (name, description)
    @Override
    @PatchMapping("/{workspaceId}")
    public ResponseEntity<RsData<WorkspaceInfoRes>> updateWorkspace(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @Valid @RequestBody UpdateWorkspaceReq request) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(
                new RsData<>(
                        workspaceService.updateWorkspace(workspaceId, memberId, request),
                        "워크스페이스 수정 성공"
                )
        );
    }

    // Workspace 삭제 (소프트 삭제)
    @Override
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<RsData<Void>> deleteWorkspace(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        workspaceService.deleteWorkspace(workspaceId, memberId);
        return ResponseEntity.ok(
                new RsData<>("워크스페이스가 삭제되었습니다.")
        );
    }

    // Workspace 멤버 목록 조회
    @Override
    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<RsData<List<WorkspaceMemberInfoRes>>> listMembers(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember, @PathVariable long workspaceId) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(
                new RsData<>(
                        workspaceService.listMembers(workspaceId, memberId),
                        "워크스페이스 멤버 목록 조회 성공"
                )
        );
    }

    // Workspace 멤버 역할 변경
    @Override
    @PatchMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<RsData<Void>> changeMemberRole(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @PathVariable long memberId,
            @Valid @RequestBody UpdateWorkspaceRoleReq request) {
        long requesterId = resolveAuthenticatedMemberId(authenticatedMember);
        workspaceService.changeMemberRole(workspaceId, memberId, request, requesterId);
        return ResponseEntity.ok(
                new RsData<>("워크스페이스 멤버 역할 변경 성공")
        );
    }

    // Workspace 멤버 삭제
    @Override
    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<RsData<Void>> removeMember(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @PathVariable long memberId) {
        long requesterId = resolveAuthenticatedMemberId(authenticatedMember);
        workspaceService.removeMember(workspaceId, memberId, requesterId);
        return ResponseEntity.ok(
                new RsData<>("워크스페이스 멤버 삭제 성공")
        );
    }

    // Workspace 초대 링크 생성
    @Override
    @PostMapping("/{workspaceId}/invites")
    public ResponseEntity<RsData<WorkspaceInviteInfoRes>> createInviteLink(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @Valid @RequestBody CreateWorkspaceInviteReq request) {
        long requesterId = resolveAuthenticatedMemberId(authenticatedMember);
        WorkspaceInviteInfoRes response = workspaceService.createInviteLink(workspaceId, requesterId, request);

        return ResponseEntity.created(URI.create("/api/v1/invites/" + response.token()))
                .body(new RsData<>(response, "초대 링크가 생성되었습니다."));
    }

    // Workspace 초대 목록 조회
    @Override
    @GetMapping("/{workspaceId}/invites")
    public ResponseEntity<RsData<List<WorkspaceInviteManagementRes>>> listMySentInvites(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @RequestParam(required = false) String status) {
        long requesterId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(new RsData<>(
                workspaceService.listMySentInvites(workspaceId, requesterId, status),
                "보낸 초대 목록 조회 성공"));
    }

    // Workspace 초대 삭제
    @Override
    @DeleteMapping("/{workspaceId}/invites/{inviteId}")
    public ResponseEntity<RsData<Void>> deleteInvite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @PathVariable long inviteId) {
        long requesterId = resolveAuthenticatedMemberId(authenticatedMember);
        workspaceService.deleteInvite(workspaceId, inviteId, requesterId);
        return ResponseEntity.ok(new RsData<>("초대 링크가 삭제되었습니다."));
    }

    // Workspace 초대 연장
    @Override
    @PatchMapping("/{workspaceId}/invites/{inviteId}/extend")
    public ResponseEntity<RsData<WorkspaceInviteManagementRes>> extendInvite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable long workspaceId,
            @PathVariable long inviteId,
            @Valid @RequestBody ExtendWorkspaceInviteReq request) {
        long requesterId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(new RsData<>(
                workspaceService.extendInvite(workspaceId, inviteId, requesterId, request),
                "초대 링크가 연장되었습니다."));
    }

    private long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[WorkspaceController#resolveAuthenticatedMemberId] authenticated member is missing",
                    CommonErrorCode.UNAUTHORIZED.defaultMessage());
        }

        return authenticatedMember.memberId();
    }
}
