package back.domain.artifact.dto;

public record ArtifactFileReference(String path, String name, String contentType, Long sizeBytes, boolean exists) {}
