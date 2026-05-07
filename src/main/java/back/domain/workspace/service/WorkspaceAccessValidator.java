package back.domain.workspace.service;

import back.domain.workspace.entity.Workspace;
import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;
import back.domain.workspace.repository.WorkspaceMemberRepository;
import back.domain.workspace.repository.WorkspaceRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
@Component
@RequiredArgsConstructor
public class WorkspaceAccessValidator {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * 워크스페이스 멤버십을 검증하고, 멤버가 아니면 예외를 던집니다.
     *
     * @param workspaceId 대상 워크스페이스 ID
     * @param memberId    검증할 회원 ID
     * @return 워크스페이스 멤버 정보
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 회원이 워크스페이스 멤버가 아닌 경우 (FORBIDDEN)
     */
    public WorkspaceMember requireMember(long workspaceId, long memberId) {
        getWorkspaceOrThrow(workspaceId);
        return workspaceMemberRepository
                .findByWorkspaceIdAndMemberId(workspaceId, memberId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.FORBIDDEN,
                        "[WorkspaceAccessValidator#requireMember] workspace membership not found",
                        "워크스페이스 접근 권한이 없습니다."));
    }

    /**
     * 워크스페이스 ADMIN 권한을 검증하고, ADMIN이 아니면 예외를 던집니다.
     *
     * @param workspaceId 대상 워크스페이스 ID
     * @param memberId    검증할 회원 ID
     * @return 워크스페이스 멤버 정보
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 회원이 워크스페이스 멤버가 아닌 경우 (FORBIDDEN)
     * @throws ServiceException 회원이 ADMIN이 아닌 경우 (FORBIDDEN)
     */
    public WorkspaceMember requireAdmin(long workspaceId, long memberId) {
        WorkspaceMember workspaceMember = requireMember(workspaceId, memberId);
        if (workspaceMember.getRole() != WorkspaceMemberRole.ADMIN) {
            throw new ServiceException(
                    CommonErrorCode.FORBIDDEN,
                    "[WorkspaceAccessValidator#requireAdmin] workspace member is not admin",
                    "워크스페이스 관리자 권한이 필요합니다.");
        }

        return workspaceMember;
    }

    private Workspace getWorkspaceOrThrow(long workspaceId) {
        return workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[WorkspaceAccessValidator#getWorkspaceOrThrow] workspace not found by id",
                        "워크스페이스가 존재하지 않습니다."));
    }
}
