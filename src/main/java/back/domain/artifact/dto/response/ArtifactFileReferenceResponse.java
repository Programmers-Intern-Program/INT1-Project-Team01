package back.domain.artifact.dto.response;

public record ArtifactFileReferenceResponse(
        String path, String name, String contentType, Long sizeBytes, boolean exists) {}
