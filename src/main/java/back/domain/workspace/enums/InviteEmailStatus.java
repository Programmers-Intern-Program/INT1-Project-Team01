package back.domain.workspace.enums;

public enum InviteEmailStatus {
    NOT_REQUESTED, // targetEmail이 없을 때
    PENDING,
    SENT,
    FAILED
}
