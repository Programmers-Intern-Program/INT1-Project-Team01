package back.domain.workspace.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
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
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    // Workspace 생성
    @PostMapping
    public ResponseEntity<RsData<WorkspaceInfoRes>> create(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody CreateWorkspaceReq request) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        WorkspaceInfoRes response = workspaceService.create(memberId, request);
        return ResponseEntity.ok(
                new RsData<>(
                        response,
                        "워크스페이스가 생성되었습니다."
                )
        );
    }

    // Workspace 다건(목록) 조회
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

    // Workspace 멤버 목록 조회
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
