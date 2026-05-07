package back.domain.task.dto.request;

import back.domain.task.entity.ArtifactType;

public record TaskArtifactSaveRequest(
        ArtifactType artifactType,
        String name,
        String url
) {
}