package back.domain.execution.dto.request;

public record TaskArtifactSaveRequest(String artifactType, String name, String url) {

    public TaskArtifactSaveRequest {
        artifactType = requireNotBlank(artifactType, "artifactType");
        name = requireNotBlank(name, "name");
        url = normalizeOptional(url);
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
