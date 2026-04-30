package back.domain.workspace.service;

import java.util.List;

import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;

public interface WorkspaceService {
    WorkspaceInfoRes create(long memberId, CreateWorkspaceReq request);

    List<WorkspaceSummaryInfoRes> listMyWorkspaces(long memberId);

    WorkspaceInfoRes getWorkspace(long workspaceId, long memberId);

    WorkspaceInfoRes updateWorkspace(long workspaceId, long memberId, UpdateWorkspaceReq request);

    List<WorkspaceMemberInfoRes> listMembers(long workspaceId, long memberId);

    void changeMemberRole(long workspaceId, long targetMemberId, UpdateWorkspaceRoleReq request, long requesterId);

    void removeMember(long workspaceId, long targetMemberId, long requesterId);
}
