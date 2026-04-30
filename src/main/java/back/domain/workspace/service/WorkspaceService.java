package back.domain.workspace.service;

import java.util.List;

import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
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
}
