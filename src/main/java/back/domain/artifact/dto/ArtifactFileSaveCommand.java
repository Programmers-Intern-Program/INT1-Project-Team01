package back.domain.artifact.dto;

public record ArtifactFileSaveCommand(String path, String content) {

    public ArtifactFileSaveCommand {
        path = requireNotBlank(path, "path");
        content = content == null ? "" : content;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
