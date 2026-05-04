package back.domain.workspace.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.dto.request.CreateWorkspaceInviteReq;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.response.WorkspaceInviteInfoRes;
import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceInvite;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceInviteStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceInviteRepository;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceServiceImpl implements WorkspaceService {
    private static final int DEFAULT_INVITE_EXPIRES_IN_DAYS = 7;
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 5;

    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;

    @Value("${custom.invite.base-url}")
    private String inviteBaseUrl;

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

    // Workspace 초대 링크 생성 (Admin만 가능)
    @Override
    @Transactional
    public WorkspaceInviteInfoRes createInviteLink(
            long workspaceId, long requesterId, CreateWorkspaceInviteReq request) {
        WorkspaceMember requester = requireAdmin(workspaceId, requesterId);
        Workspace workspace = requester.getWorkspace();
        Member createdByMember = requester.getMember();
        WorkspaceMemberRole role = request.role() == null ? WorkspaceMemberRole.MEMBER : request.role();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(resolveExpiresInDays(request));
        String token = generateUniqueInviteToken();

        WorkspaceInvite workspaceInvite = workspaceInviteRepository.save(
                WorkspaceInvite.create(workspace, token, role, createdByMember, expiresAt));

        return new WorkspaceInviteInfoRes(
                workspaceInvite.getId(),
                workspaceInvite.getToken(),
                buildInviteUrl(workspaceInvite.getToken()),
                workspaceInvite.getExpiresAt());
    }

    // Workspace 초대 조회 (비로그인 가능)
    @Override
    public WorkspaceInvitePreviewRes getInviteInfo(String token) {
        WorkspaceInvite workspaceInvite = getInviteOrThrow(token);
        WorkspaceInviteStatus status = workspaceInvite.getStatus(LocalDateTime.now());

        return new WorkspaceInvitePreviewRes(
                workspaceInvite.getId(),
                workspaceInvite.getWorkspace().getName(),
                workspaceInvite.getRole(),
                workspaceInvite.getExpiresAt(),
                status,
                status == WorkspaceInviteStatus.EXPIRED);
    }

    // Workspace 초대 수락
    @Override
    @Transactional
    public void acceptInvite(String token, long memberId) {
        Member member = getMemberOrThrow(memberId);
        WorkspaceInvite workspaceInvite = getInviteOrThrow(token);
        validateInviteCanBeAccepted(workspaceInvite);

        Workspace workspace = workspaceInvite.getWorkspace();
        if (workspaceMemberRepository.existsByWorkspaceIdAndMemberId(workspace.getId(), memberId)) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[WorkspaceServiceImpl#acceptInvite] member already joined workspace",
                    "이미 워크스페이스 멤버입니다.");
        }

        workspaceMemberRepository.save(WorkspaceMember.create(workspace, member, workspaceInvite.getRole()));
        workspaceInvite.accept(member);
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

    private WorkspaceInvite getInviteOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "[WorkspaceServiceImpl#getInviteOrThrow] invite token is blank",
                    "초대 링크가 존재하지 않습니다.");
        }

        return workspaceInviteRepository.findByToken(token.trim()).orElseThrow(() -> new ServiceException(
                CommonErrorCode.NOT_FOUND,
                "[WorkspaceServiceImpl#getInviteOrThrow] invite not found by token",
                "초대 링크가 존재하지 않습니다."));
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

    private int resolveExpiresInDays(CreateWorkspaceInviteReq request) {
        if (request.expiresInDays() == null) {
            return DEFAULT_INVITE_EXPIRES_IN_DAYS;
        }
        return request.expiresInDays();
    }

    private String generateUniqueInviteToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String token = UUID.randomUUID().toString();
            if (!workspaceInviteRepository.existsByToken(token)) {
                return token;
            }
        }

        throw new ServiceException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                "[WorkspaceServiceImpl#generateUniqueInviteToken] failed to generate unique invite token",
                "초대 링크 생성에 실패했습니다.");
    }

    private String buildInviteUrl(String token) {
        String normalizedBaseUrl = inviteBaseUrl == null || inviteBaseUrl.isBlank()
                ? "http://localhost:8080/api/v1/invites"
                : inviteBaseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            return normalizedBaseUrl + token + "/accept";
        }
        return normalizedBaseUrl + "/" + token + "/accept";
    }

    private void validateInviteCanBeAccepted(WorkspaceInvite workspaceInvite) {
        WorkspaceInviteStatus status = workspaceInvite.getStatus(LocalDateTime.now());
        if (status == WorkspaceInviteStatus.PENDING) {
            return;
        }

        if (status == WorkspaceInviteStatus.ACCEPTED) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[WorkspaceServiceImpl#validateInviteCanBeAccepted] invite already accepted",
                    "이미 사용된 초대 링크입니다.");
        }

        if (status == WorkspaceInviteStatus.EXPIRED) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[WorkspaceServiceImpl#validateInviteCanBeAccepted] invite expired",
                    "만료된 초대 링크입니다.");
        }

        throw new ServiceException(
                CommonErrorCode.BAD_REQUEST_STATE,
                "[WorkspaceServiceImpl#validateInviteCanBeAccepted] invite revoked",
                "폐기된 초대 링크입니다.");
    }
}
