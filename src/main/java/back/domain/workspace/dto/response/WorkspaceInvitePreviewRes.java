package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

import back.domain.workspace.enums.WorkspaceInviteStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;

public record WorkspaceInvitePreviewRes(
        Long inviteId,
        String workspaceName,
        WorkspaceMemberRole role,
        LocalDateTime expiresAt,
        WorkspaceInviteStatus status,
        boolean expired
) {
}
