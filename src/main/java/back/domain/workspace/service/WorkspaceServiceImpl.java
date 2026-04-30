package back.domain.workspace.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceServiceImpl implements WorkspaceService {

    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    // Workspace 생성
    @Override
    @Transactional
    public WorkspaceInfoRes create(long memberId, CreateWorkspaceReq request) {
        Member creator = getMemberOrThrow(memberId);
        Workspace workspace = workspaceRepository.save(
                Workspace.create(request.name(), request.description(), creator)
        );
        WorkspaceMember workspaceMember =
                workspaceMemberRepository.save(WorkspaceMember.create(workspace, creator, WorkspaceMemberRole.ADMIN));

        return toWorkspaceResponse(workspace, workspaceMember.getRole());
    }

    // Workspace 목록 조회
    @Override
    public List<WorkspaceSummaryInfoRes> listMyWorkspaces(long memberId) {
        getMemberOrThrow(memberId);
        return workspaceMemberRepository.findAllByMemberIdWithWorkspace(memberId).stream()
                .sorted(Comparator.comparing(workspaceMember -> workspaceMember.getWorkspace().getId()))
                .map(this::toWorkspaceSummaryResponse)
                .toList();
    }

    // Workspace 상세 조회
    @Override
    public WorkspaceInfoRes getWorkspace(long workspaceId, long memberId) {
        WorkspaceMember workspaceMember = requireMember(workspaceId, memberId);
        return toWorkspaceResponse(workspaceMember.getWorkspace(), workspaceMember.getRole());
    }

    // Workspace 업데이트 (name, description)
    @Override
    @Transactional
    public WorkspaceInfoRes updateWorkspace(long workspaceId, long memberId, UpdateWorkspaceReq request) {
        WorkspaceMember workspaceMember = requireAdmin(workspaceId, memberId);
        Workspace workspace = workspaceMember.getWorkspace();
        workspace.update(request.name(), request.description());

        return toWorkspaceResponse(workspace, workspaceMember.getRole());
    }

    // Workspace 멤버 조회
    @Override
    public List<WorkspaceMemberInfoRes> listMembers(long workspaceId, long memberId) {
        requireMember(workspaceId, memberId);
        return workspaceMemberRepository.findAllByWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(workspaceMember -> workspaceMember.getMember().getId()))
                .map(this::toWorkspaceMemberResponse)
                .toList();
    }

    // Workspace 멤버 role 변경 (Admin만 가능)
    @Override
    @Transactional
    public void changeMemberRole(
            long workspaceId, long targetMemberId, UpdateWorkspaceRoleReq request, long requesterId) {
        requireAdmin(workspaceId, requesterId);
        WorkspaceMember targetMember = requireMember(workspaceId, targetMemberId);
        validateLastAdminIsKept(targetMember, request.role());
        targetMember.changeRole(request.role());
    }

    // Workspace 멤버 삭제 (Admin만 가능)
    @Override
    @Transactional
    public void removeMember(long workspaceId, long targetMemberId, long requesterId) {
        requireAdmin(workspaceId, requesterId);
        WorkspaceMember targetMember = requireMember(workspaceId, targetMemberId);
        validateLastAdminIsKept(targetMember, null);
        workspaceMemberRepository.delete(targetMember);
    }

    // ========= util =========

    private Member getMemberOrThrow(long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new ServiceException(
                CommonErrorCode.NOT_FOUND,
                "[WorkspaceServiceImpl#getMemberOrThrow] member not found by id",
                "회원이 존재하지 않습니다."
        ));
    }

    private Workspace getWorkspaceOrThrow(long workspaceId) {
        return workspaceRepository.findById(workspaceId).orElseThrow(() -> new ServiceException(
                CommonErrorCode.NOT_FOUND,
                "[WorkspaceServiceImpl#getWorkspaceOrThrow] workspace not found by id",
                "워크스페이스가 존재하지 않습니다."));
    }

    private WorkspaceMember requireMember(long workspaceId, long memberId) {
        getWorkspaceOrThrow(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndMemberId(workspaceId, memberId).orElseThrow(
                () -> new ServiceException(
                        CommonErrorCode.FORBIDDEN,
                        "[WorkspaceServiceImpl#requireMember] workspace membership not found",
                        "워크스페이스 접근 권한이 없습니다."));
    }

    private WorkspaceMember requireAdmin(long workspaceId, long memberId) {
        WorkspaceMember workspaceMember = requireMember(workspaceId, memberId);
        if (workspaceMember.getRole() != WorkspaceMemberRole.ADMIN) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[WorkspaceServiceImpl#requireAdmin] workspace member is not admin",
                    "워크스페이스 관리자 권한이 필요합니다.");
        }

        return workspaceMember;
    }

    private void validateLastAdminIsKept(WorkspaceMember targetMember, WorkspaceMemberRole nextRole) {
        if (targetMember.getRole() != WorkspaceMemberRole.ADMIN || nextRole == WorkspaceMemberRole.ADMIN) {
            return;
        }

        long adminCount = workspaceMemberRepository.countByWorkspaceIdAndRole(
                targetMember.getWorkspace().getId(), WorkspaceMemberRole.ADMIN);
        if (adminCount <= 1) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[WorkspaceServiceImpl#validateLastAdminIsKept] last admin cannot be changed or removed",
                    "워크스페이스의 마지막 관리자는 변경하거나 제거할 수 없습니다.");
        }
    }

    private WorkspaceInfoRes toWorkspaceResponse(Workspace workspace, WorkspaceMemberRole myRole) {
        return new WorkspaceInfoRes(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getCreatedByMember().getId(),
                myRole,
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }

    private WorkspaceSummaryInfoRes toWorkspaceSummaryResponse(WorkspaceMember workspaceMember) {
        Workspace workspace = workspaceMember.getWorkspace();
        return new WorkspaceSummaryInfoRes(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspaceMember.getRole(),
                0,  // TODO: openclaw 연동 시 실제 agent 수로 수정
                0, // TODO: openclaw 연동 시 실제 task 수로 수정
                workspace.getCreatedAt());
    }

    private WorkspaceMemberInfoRes toWorkspaceMemberResponse(WorkspaceMember workspaceMember) {
        Member member = workspaceMember.getMember();
        return new WorkspaceMemberInfoRes(
                member.getId(),
                member.getName(),
                member.getEmail(),
                workspaceMember.getRole(),
                workspaceMember.getJoinedAt());
    }
}
