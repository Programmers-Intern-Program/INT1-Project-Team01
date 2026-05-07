package back.domain.workspace.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import back.domain.member.entity.Member;
import back.domain.member.repository.MemberRepository;
import back.domain.workspace.email.InviteEmailCommand;
import back.domain.workspace.dto.request.CreateWorkspaceInviteReq;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.ExtendWorkspaceInviteReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceInviteInfoRes;
import back.domain.workspace.dto.response.WorkspaceInviteManagementRes;
import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
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
    private final InviteEmailService inviteEmailService;
    private final WorkspaceAccessValidator workspaceAccessValidator;

    @Value("${custom.invite.base-url}")
    private String inviteBaseUrl;

    // Workspace 생성
    @Override
    @Transactional
    public WorkspaceInfoRes create(long memberId, CreateWorkspaceReq request) {
        Member creator = getMemberOrThrow(memberId);
        Workspace workspace =
                workspaceRepository.save(Workspace.create(request.name(), request.description(), creator));
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
        WorkspaceMember workspaceMember = workspaceAccessValidator.requireMember(workspaceId, memberId);
        return toWorkspaceResponse(workspaceMember.getWorkspace(), workspaceMember.getRole());
    }

    // Workspace 업데이트 (name, description)
    @Override
    @Transactional
    public WorkspaceInfoRes updateWorkspace(long workspaceId, long memberId, UpdateWorkspaceReq request) {
        WorkspaceMember workspaceMember = workspaceAccessValidator.requireAdmin(workspaceId, memberId);
        Workspace workspace = workspaceMember.getWorkspace();
        workspace.update(request.name(), request.description());

        return toWorkspaceResponse(workspace, workspaceMember.getRole());
    }

    // Workspace 멤버 조회
    @Override
    public List<WorkspaceMemberInfoRes> listMembers(long workspaceId, long memberId) {
        workspaceAccessValidator.requireMember(workspaceId, memberId);
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
        workspaceAccessValidator.requireAdmin(workspaceId, requesterId);
        WorkspaceMember targetMember = workspaceAccessValidator.requireMember(workspaceId, targetMemberId);
        validateLastAdminIsKept(targetMember, request.role());
        targetMember.changeRole(request.role());
    }

    // Workspace 멤버 삭제 (Admin만 가능)
    @Override
    @Transactional
    public void removeMember(long workspaceId, long targetMemberId, long requesterId) {
        workspaceAccessValidator.requireAdmin(workspaceId, requesterId);
        WorkspaceMember targetMember = workspaceAccessValidator.requireMember(workspaceId, targetMemberId);
        validateLastAdminIsKept(targetMember, null);
        workspaceMemberRepository.delete(targetMember);
    }

    // Workspace 초대 링크 생성 (Admin만 가능)
    @Override
    @Transactional
    public WorkspaceInviteInfoRes createInviteLink(
            long workspaceId, long requesterId, CreateWorkspaceInviteReq request) {
        WorkspaceMember requester = workspaceAccessValidator.requireAdmin(workspaceId, requesterId);
        Workspace workspace = requester.getWorkspace();
        Member createdByMember = requester.getMember();
        WorkspaceMemberRole role = request.role() == null ? WorkspaceMemberRole.MEMBER : request.role();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(resolveExpiresInDays(request));
        String token = generateUniqueInviteToken();

        WorkspaceInvite workspaceInvite = WorkspaceInvite.create(workspace, token, role, createdByMember, expiresAt);
        workspaceInvite.requestEmailDelivery(request.targetEmail());
        workspaceInvite = workspaceInviteRepository.save(workspaceInvite);
        String inviteUrl = buildInviteUrl(workspaceInvite.getToken());
        sendInviteEmailIfNeeded(workspace, createdByMember, workspaceInvite, inviteUrl, request.targetEmail());

        return new WorkspaceInviteInfoRes(
                workspaceInvite.getId(),
                workspaceInvite.getToken(),
                inviteUrl,
                workspaceInvite.getExpiresAt()
        );
    }

    // 사용자가 보낸 Workspace 초대 목록 조회
    @Override
    public List<WorkspaceInviteManagementRes> listMySentInvites(
            long workspaceId,
            long requesterId,
            String status) {
        workspaceAccessValidator.requireAdmin(workspaceId, requesterId);
        WorkspaceInviteStatus statusFilter = parseInviteStatus(status);
        return workspaceInviteRepository
                .findAllByWorkspaceIdAndCreatedByMemberIdOrderByIdDesc(workspaceId, requesterId)
                .stream()
                .filter(workspaceInvite -> workspaceInvite.getStatus(LocalDateTime.now()) == statusFilter)
                .map(this::toWorkspaceInviteManagementResponse)
                .toList();
    }

    // Workspace 초대 삭제(폐기)
    @Override
    @Transactional
    public void deleteInvite(long workspaceId, long inviteId, long requesterId) {
        workspaceAccessValidator.requireAdmin(workspaceId, requesterId);
        WorkspaceInvite workspaceInvite = getSentInviteOrThrow(workspaceId, inviteId, requesterId);
        validateInviteCanBeDeleted(workspaceInvite);
        workspaceInvite.revoke();
    }

    // Workspace 초대 만료일 연장
    @Override
    @Transactional
    public WorkspaceInviteManagementRes extendInvite(
            long workspaceId,
            long inviteId,
            long requesterId,
            ExtendWorkspaceInviteReq request) {
        workspaceAccessValidator.requireAdmin(workspaceId, requesterId);
        WorkspaceInvite workspaceInvite = getSentInviteOrThrow(workspaceId, inviteId, requesterId);
        validateInviteCanBeExtended(workspaceInvite);

        int additionalDays = request.additionalDays() == null ? DEFAULT_INVITE_EXPIRES_IN_DAYS : request.additionalDays();
        workspaceInvite.extendExpiresAt(additionalDays);
        return toWorkspaceInviteManagementResponse(workspaceInvite);
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
        validateInviteTargetEmailIfNeeded(workspaceInvite, member);

        Workspace workspace = workspaceInvite.getWorkspace();
        if (workspaceMemberRepository.existsByWorkspaceIdAndMemberId(workspace.getId(), memberId)) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[WorkspaceServiceImpl#acceptInvite] member already joined workspace",
                    "이미 워크스페이스 멤버입니다.");
        }

        workspaceMemberRepository.save(WorkspaceMember.create(workspace, member, workspaceInvite.getRole()));
        if (workspaceInvite.hasTargetEmail()) {
            workspaceInvite.accept(member);
        }
    }

    // ========= util =========

    // memberId로 회원을 조회하고, 없으면 예외를 던진다
    private Member getMemberOrThrow(long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceServiceImpl#getMemberOrThrow] member not found by id",
                        "회원이 존재하지 않습니다."));
    }

    // 토큰으로 초대를 조회하고, 없거나 토큰이 blank면 예외를 던진다
    private WorkspaceInvite getInviteOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.NOT_FOUND,
                    "[WorkspaceServiceImpl#getInviteOrThrow] invite token is blank",
                    "초대 링크가 존재하지 않습니다.");
        }

        return workspaceInviteRepository
                .findByToken(token.trim())
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceServiceImpl#getInviteOrThrow] invite not found by token",
                        "초대 링크가 존재하지 않습니다."));
    }

    // 초대 ID로 내가 보낸 초대를 조회하고, 없으면 예외를 던진다
    private WorkspaceInvite getSentInviteOrThrow(long workspaceId, long inviteId, long requesterId) {
        return workspaceInviteRepository
                .findByIdAndWorkspaceIdAndCreatedByMemberId(inviteId, workspaceId, requesterId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceServiceImpl#getSentInviteOrThrow] sent invite not found",
                        "초대 링크가 존재하지 않습니다."));
    }

    // 마지막 ADMIN을 변경하거나 제거하려 할 때 예외를 던진다
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

    // Workspace 엔티티를 WorkspaceInfoRes DTO로 변환한다
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

    // WorkspaceMember 엔티티를 WorkspaceSummaryInfoRes DTO로 변환한다
    private WorkspaceSummaryInfoRes toWorkspaceSummaryResponse(WorkspaceMember workspaceMember) {
        Workspace workspace = workspaceMember.getWorkspace();
        return new WorkspaceSummaryInfoRes(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspaceMember.getRole(),
                0, // TODO: openclaw 연동 시 실제 agent 수로 수정
                0, // TODO: openclaw 연동 시 실제 task 수로 수정
                workspace.getCreatedAt());
    }

    // WorkspaceMember 엔티티를 WorkspaceMemberInfoRes DTO로 변환한다
    private WorkspaceMemberInfoRes toWorkspaceMemberResponse(WorkspaceMember workspaceMember) {
        Member member = workspaceMember.getMember();
        return new WorkspaceMemberInfoRes(
                member.getId(),
                member.getName(),
                member.getEmail(),
                workspaceMember.getRole(),
                workspaceMember.getJoinedAt());
    }

    // WorkspaceInvite 엔티티를 초대 관리 응답 DTO로 변환한다
    private WorkspaceInviteManagementRes toWorkspaceInviteManagementResponse(WorkspaceInvite workspaceInvite) {
        return new WorkspaceInviteManagementRes(
                workspaceInvite.getId(),
                workspaceInvite.getToken(),
                buildInviteUrl(workspaceInvite.getToken()),
                workspaceInvite.getRole(),
                workspaceInvite.getTargetEmail(),
                workspaceInvite.getEmailStatus(),
                workspaceInvite.getCreatedByMember().getId(),
                workspaceInvite.getCreatedByMember().getName(),
                workspaceInvite.getExpiresAt(),
                workspaceInvite.getStatus(LocalDateTime.now()),
                workspaceInvite.getEmailSentAt(),
                workspaceInvite.getAcceptedAt(),
                workspaceInvite.getRevokedAt());
    }

    // 초대 만료일을 결정한다. null이면 기본값(7일)을 반환한다
    private int resolveExpiresInDays(CreateWorkspaceInviteReq request) {
        if (request.expiresInDays() == null) {
            return DEFAULT_INVITE_EXPIRES_IN_DAYS;
        }
        return request.expiresInDays();
    }

    // 중복되지 않는 초대 토큰을 생성한다. 최대 시도 횟수 초과 시 예외를 던진다
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

    // 초대 토큰을 기반으로 수락 URL을 생성한다
    private String buildInviteUrl(String token) {
        String normalizedBaseUrl = inviteBaseUrl == null || inviteBaseUrl.isBlank()
                ? "http://localhost:8080/api/v1/invites"
                : inviteBaseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            return normalizedBaseUrl + token + "/accept";
        }
        return normalizedBaseUrl + "/" + token + "/accept";
    }

    // 초대 목록 상태 필터를 파싱한다. null/blank면 PENDING을 기본값으로 사용한다
    private WorkspaceInviteStatus parseInviteStatus(String status) {
        if (status == null || status.isBlank()) {
            return WorkspaceInviteStatus.PENDING;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            return WorkspaceInviteStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST,
                    "[WorkspaceServiceImpl#parseInviteStatus] invalid invite status. status=" + status,
                    "초대 상태 값이 올바르지 않습니다.");
        }
    }

    // 초대 수락 가능 여부를 검증하고, 불가능한 상태면 예외를 던진다
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

    // 초대 삭제 가능 여부를 검증한다
    private void validateInviteCanBeDeleted(WorkspaceInvite workspaceInvite) {
        WorkspaceInviteStatus status = workspaceInvite.getStatus(LocalDateTime.now());
        if (status == WorkspaceInviteStatus.ACCEPTED) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[WorkspaceServiceImpl#validateInviteCanBeDeleted] accepted invite cannot be deleted",
                    "이미 수락된 초대 링크는 삭제할 수 없습니다.");
        }
    }

    // 초대 연장 가능 여부를 검증한다
    private void validateInviteCanBeExtended(WorkspaceInvite workspaceInvite) {
        WorkspaceInviteStatus status = workspaceInvite.getStatus(LocalDateTime.now());
        if (status == WorkspaceInviteStatus.ACCEPTED) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[WorkspaceServiceImpl#validateInviteCanBeExtended] accepted invite cannot be extended",
                    "이미 수락된 초대 링크는 연장할 수 없습니다.");
        }

        if (status == WorkspaceInviteStatus.REVOKED) {
            throw new ServiceException(
                    CommonErrorCode.BAD_REQUEST_STATE,
                    "[WorkspaceServiceImpl#validateInviteCanBeExtended] revoked invite cannot be extended",
                    "폐기된 초대 링크는 연장할 수 없습니다.");
        }
    }

    // 이메일 초대인 경우 수락자의 이메일이 초대 대상과 일치하는지 검증한다
    private void validateInviteTargetEmailIfNeeded(WorkspaceInvite workspaceInvite, Member member) {
        if (!workspaceInvite.hasTargetEmail()) {
            return;
        }

        if (workspaceInvite.getTargetEmail().equalsIgnoreCase(member.getEmail())) {
            return;
        }

        throw new ServiceException(
                CommonErrorCode.FORBIDDEN,
                "[WorkspaceServiceImpl#validateInviteTargetEmailIfNeeded] invite target email does not match member email",
                "초대 대상 이메일과 로그인한 회원 이메일이 일치하지 않습니다.");
    }

    // targetEmail이 존재할 때만 트랜잭션 커밋 이후 초대 이메일을 비동기 발송
    private void sendInviteEmailIfNeeded(
            Workspace workspace,
            Member createdByMember,
            WorkspaceInvite workspaceInvite,
            String inviteUrl,
            String targetEmail) {
        if (targetEmail == null || targetEmail.isBlank()) {
            return;
        }

        InviteEmailCommand command = new InviteEmailCommand(
                workspace.getId(),
                workspace.getName(),
                workspaceInvite.getId(),
                inviteUrl,
                workspaceInvite.getRole(),
                workspaceInvite.getExpiresAt(),
                createdByMember.getId(),
                createdByMember.getName(),
                targetEmail.trim());

        // 실제 메일 발송은 지금 시작하지 않고, 초대 저장 트랜잭션이 커밋된 뒤 시작한다.
        runAfterCommit(() -> inviteEmailService.sendAsync(command));
    }

    // 현재 트랜잭션이 성공적으로 커밋된 뒤에 task를 실행한다.
    // 예: 초대 이메일 발송은 저장된 초대 row가 DB에 확정된 뒤 시작해야 async 스레드가 inviteId를 조회할 수 있다.
    private void runAfterCommit(Runnable task) {
        // 단위 테스트나 트랜잭션 밖 호출처럼 동기화 컨텍스트가 없으면 예약할 곳이 없으므로 바로 실행한다.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }

        // 트랜잭션 안이면 지금 바로 실행하지 않고, Spring이 커밋 성공 후 afterCommit을 호출할 때 실행한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
