package back.domain.artifact.dto;

public record ArtifactFileContent(String path, String name, String contentType, long sizeBytes, String content) {}
