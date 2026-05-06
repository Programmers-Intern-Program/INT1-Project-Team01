package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

import back.domain.workspace.enums.InviteEmailStatus;
import back.domain.workspace.enums.WorkspaceInviteStatus;
import back.domain.workspace.enums.WorkspaceMemberRole;

public record WorkspaceInviteManagementRes(
        Long inviteId,
        String token,
        String inviteUrl,
        WorkspaceMemberRole role,
        String targetEmail,
        InviteEmailStatus emailStatus,
        Long createdByMemberId,
        String createdByMemberName,
        LocalDateTime expiresAt,
        WorkspaceInviteStatus status,
        LocalDateTime emailSentAt,
        LocalDateTime acceptedAt,
        LocalDateTime revokedAt
) {
}
