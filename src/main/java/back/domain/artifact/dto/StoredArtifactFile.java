package back.domain.artifact.dto;

public record StoredArtifactFile(String relativePath, long sizeBytes) {

    public StoredArtifactFile {
        relativePath = requireNotBlank(relativePath, "relativePath");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
