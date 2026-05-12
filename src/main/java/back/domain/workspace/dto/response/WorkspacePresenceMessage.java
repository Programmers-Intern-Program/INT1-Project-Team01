package back.domain.workspace.dto.response;

import back.domain.workspace.dto.WorkspacePresencePosition;

import java.util.List;

public record WorkspacePresenceMessage(
        String type,
        long workspaceId,
        Long memberId,
        WorkspacePresencePosition position,
        String status,
        List<WorkspacePresenceMember> members,
        String message) {
    public WorkspacePresenceMessage {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static WorkspacePresenceMessage snapshot(long workspaceId, List<WorkspacePresenceMember> members) {
        return new WorkspacePresenceMessage(
                WorkspacePresenceMessageType.SNAPSHOT.value(),
                workspaceId,
                null,
                null,
                null,
                members,
                null);
    }

    public static WorkspacePresenceMessage join(long workspaceId, long memberId) {
        return memberEvent(WorkspacePresenceMessageType.JOIN, workspaceId, memberId);
    }

    public static WorkspacePresenceMessage leave(long workspaceId, long memberId) {
        return memberEvent(WorkspacePresenceMessageType.LEAVE, workspaceId, memberId);
    }

    public static WorkspacePresenceMessage position(
            long workspaceId, long memberId, WorkspacePresencePosition position, String status) {
        return new WorkspacePresenceMessage(
                WorkspacePresenceMessageType.POSITION.value(),
                workspaceId,
                memberId,
                position,
                status,
                List.of(),
                null);
    }

    public static WorkspacePresenceMessage error(long workspaceId, String message) {
        return new WorkspacePresenceMessage(
                WorkspacePresenceMessageType.ERROR.value(), workspaceId, null, null, null, List.of(), message);
    }

    private static WorkspacePresenceMessage memberEvent(
            WorkspacePresenceMessageType type, long workspaceId, long memberId) {
        return new WorkspacePresenceMessage(type.value(), workspaceId, memberId, null, null, List.of(), null);
    }
}
