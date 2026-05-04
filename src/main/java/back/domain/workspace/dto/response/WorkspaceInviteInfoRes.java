package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

public record WorkspaceInviteInfoRes(
        Long inviteId,
        String token,
        String inviteUrl,
        LocalDateTime expiresAt
) {
}
