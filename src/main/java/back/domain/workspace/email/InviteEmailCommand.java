package back.domain.workspace.email;

import java.time.LocalDateTime;

import back.domain.workspace.enums.WorkspaceMemberRole;

public record InviteEmailCommand(
        Long workspaceId,
        String workspaceName,
        Long inviteId,
        String inviteUrl,
        WorkspaceMemberRole role,
        LocalDateTime expiresAt,
        Long createdByMemberId,
        String inviterName,
        String targetEmail) {}
