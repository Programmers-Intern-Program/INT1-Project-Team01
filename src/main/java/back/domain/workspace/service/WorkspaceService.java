package back.domain.workspace.service;

import java.util.List;

import back.domain.workspace.dto.request.CreateWorkspaceInviteReq;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.ExtendWorkspaceInviteReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.response.WorkspaceInviteInfoRes;
import back.domain.workspace.dto.response.WorkspaceInviteManagementRes;
import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;

public interface WorkspaceService {

    /**
     * 새 워크스페이스를 생성하고 요청자를 ADMIN으로 등록합니다.
     *
     * @param memberId 생성 요청자의 회원 ID
     * @param request  워크스페이스 생성 요청 DTO
     * @return 생성된 워크스페이스 정보
     * @throws ServiceException 회원이 존재하지 않는 경우 (NOT_FOUND)
     */
    WorkspaceInfoRes create(long memberId, CreateWorkspaceReq request);

    /**
     * 요청자가 속한 워크스페이스 목록을 반환합니다.
     *
     * @param memberId 조회 요청자의 회원 ID
     * @return 요청자가 속한 워크스페이스 요약 정보 목록 (워크스페이스 ID 오름차순)
     * @throws ServiceException 회원이 존재하지 않는 경우 (NOT_FOUND)
     */
    List<WorkspaceSummaryInfoRes> listMyWorkspaces(long memberId);

    /**
     * 워크스페이스 상세 정보를 반환합니다.
     *
     * @param workspaceId 조회할 워크스페이스 ID
     * @param memberId    요청자의 회원 ID
     * @return 워크스페이스 상세 정보
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 해당 워크스페이스의 멤버가 아닌 경우 (FORBIDDEN)
     */
    WorkspaceInfoRes getWorkspace(long workspaceId, long memberId);

    /**
     * 워크스페이스의 이름 또는 설명을 수정합니다.
     *
     * @param workspaceId 수정할 워크스페이스 ID
     * @param memberId    요청자의 회원 ID
     * @param request     수정 요청 DTO (name, description 중 null 이 아닌 필드만 반영)
     * @return 수정된 워크스페이스 정보
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     */
    WorkspaceInfoRes updateWorkspace(long workspaceId, long memberId, UpdateWorkspaceReq request);

    /**
     * 워크스페이스를 소프트 삭제합니다. 삭제된 워크스페이스는 모든 조회에서 제외됩니다.
     *
     * @param workspaceId 삭제할 워크스페이스 ID
     * @param memberId    요청자의 회원 ID
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     */
    void deleteWorkspace(long workspaceId, long memberId);

    /**
     * 워크스페이스에 속한 멤버 목록을 반환합니다.
     *
     * @param workspaceId 조회할 워크스페이스 ID
     * @param memberId    요청자의 회원 ID
     * @return 워크스페이스 멤버 정보 목록 (회원 ID 오름차순)
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 해당 워크스페이스의 멤버가 아닌 경우 (FORBIDDEN)
     */
    List<WorkspaceMemberInfoRes> listMembers(long workspaceId, long memberId);

    /**
     * 워크스페이스 멤버의 역할을 변경합니다.
     *
     * @param workspaceId    대상 워크스페이스 ID
     * @param targetMemberId 역할을 변경할 대상 회원 ID
     * @param request        변경할 역할 정보 DTO
     * @param requesterId    요청자의 회원 ID
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     * @throws ServiceException 대상 멤버가 워크스페이스에 속하지 않는 경우 (FORBIDDEN)
     * @throws ServiceException 마지막 ADMIN의 역할을 변경하려는 경우 (BAD_REQUEST_STATE)
     */
    void changeMemberRole(long workspaceId, long targetMemberId, UpdateWorkspaceRoleReq request, long requesterId);

    /**
     * 워크스페이스에서 멤버를 제거합니다.
     *
     * @param workspaceId    대상 워크스페이스 ID
     * @param targetMemberId 제거할 대상 회원 ID
     * @param requesterId    요청자의 회원 ID
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     * @throws ServiceException 대상 멤버가 워크스페이스에 속하지 않는 경우 (FORBIDDEN)
     * @throws ServiceException 마지막 ADMIN을 제거하려는 경우 (BAD_REQUEST_STATE)
     */
    void removeMember(long workspaceId, long targetMemberId, long requesterId);

    /**
     * 워크스페이스 초대 링크를 생성합니다.
     *
     * @param workspaceId  대상 워크스페이스 ID
     * @param requesterId  요청자의 회원 ID
     * @param request      초대 링크 생성 요청 DTO
     * @return 생성된 초대 링크 정보
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     */
    WorkspaceInviteInfoRes createInviteLink(long workspaceId, long requesterId, CreateWorkspaceInviteReq request);

    /**
     * 요청자가 해당 워크스페이스에서 생성한 초대 목록을 반환합니다.
     *
     * @param workspaceId 대상 워크스페이스 ID
     * @param requesterId 요청자의 회원 ID
     * @param status      초대 상태 필터 문자열 (null 또는 blank이면 PENDING)
     * @return 요청자가 생성한 초대 관리 정보 목록
     * @throws ServiceException 워크스페이스가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     */
    List<WorkspaceInviteManagementRes> listMySentInvites(
            long workspaceId,
            long requesterId,
            String status);

    /**
     * 요청자가 생성한 초대 링크를 폐기합니다.
     *
     * @param workspaceId  대상 워크스페이스 ID
     * @param inviteId     폐기할 초대 ID
     * @param requesterId  요청자의 회원 ID
     * @throws ServiceException 워크스페이스 또는 초대가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     * @throws ServiceException 이미 수락된 초대인 경우 (CONFLICT)
     */
    void deleteInvite(long workspaceId, long inviteId, long requesterId);

    /**
     * 요청자가 생성한 초대 링크 만료일을 연장합니다.
     *
     * @param workspaceId  대상 워크스페이스 ID
     * @param inviteId     연장할 초대 ID
     * @param requesterId  요청자의 회원 ID
     * @param request      연장 요청 DTO
     * @return 연장된 초대 관리 정보
     * @throws ServiceException 워크스페이스 또는 초대가 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 요청자가 ADMIN이 아닌 경우 (FORBIDDEN)
     * @throws ServiceException 이미 수락 또는 폐기된 초대인 경우 (BAD_REQUEST_STATE 또는 CONFLICT)
     */
    WorkspaceInviteManagementRes extendInvite(
            long workspaceId,
            long inviteId,
            long requesterId,
            ExtendWorkspaceInviteReq request);

    /**
     * 초대 토큰으로 초대 대상 Workspace 정보를 조회합니다.
     *
     * @param token 초대 토큰
     * @return 초대 대상 Workspace와 초대 상태 정보
     * @throws ServiceException 초대 토큰이 존재하지 않는 경우 (NOT_FOUND)
     */
    WorkspaceInvitePreviewRes getInviteInfo(String token);

    /**
     * 초대 토큰을 수락해 요청자를 Workspace 멤버로 등록합니다.
     *
     * @param token    초대 토큰
     * @param memberId 수락 요청자의 회원 ID
     * @throws ServiceException 초대 토큰 또는 회원이 존재하지 않는 경우 (NOT_FOUND)
     * @throws ServiceException 초대가 만료, 사용, 폐기된 경우 (BAD_REQUEST_STATE 또는 CONFLICT)
     * @throws ServiceException 이미 Workspace 멤버인 경우 (CONFLICT)
     */
    void acceptInvite(String token, long memberId);
}
