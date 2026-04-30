package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

import back.domain.workspace.enums.WorkspaceMemberRole;

public record WorkspaceInfoRes(
        Long workspaceId,
        String name,
        String description,
        Long createdByMemberId,
        WorkspaceMemberRole myRole,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
