package back.domain.artifact.dto.response;

public record ArtifactFileContentResponse(
        Long workspaceId, String path, String name, String contentType, long sizeBytes, String content) {}
