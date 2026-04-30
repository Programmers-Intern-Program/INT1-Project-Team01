package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

import back.domain.workspace.enums.WorkspaceMemberRole;

public record WorkspaceSummaryInfoRes(
        Long workspaceId,
        String name,
        String description,
        WorkspaceMemberRole myRole,
        int agentCount,
        int runningTaskCount,
        LocalDateTime createdAt
) {
}
