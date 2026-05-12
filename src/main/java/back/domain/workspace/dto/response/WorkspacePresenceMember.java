package back.domain.workspace.dto.response;

import back.domain.workspace.dto.WorkspacePresencePosition;

public record WorkspacePresenceMember(long memberId, WorkspacePresencePosition position, String status) {}
