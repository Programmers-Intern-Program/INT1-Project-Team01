package back.domain.workspace.dto.response;

public enum WorkspacePresenceMessageType {
    SNAPSHOT("presence.snapshot"),
    JOIN("presence.join"),
    LEAVE("presence.leave"),
    POSITION("presence.position"),
    ERROR("presence.error");

    private final String value;

    WorkspacePresenceMessageType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
