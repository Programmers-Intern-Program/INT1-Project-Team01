package back.domain.task.dto.response;

import back.domain.task.domain.ArtifactType;
import back.domain.task.domain.TaskArtifact;

public record TaskArtifactResponse(
        Long artifactId,
        ArtifactType artifactType,
        String name,
        String url
) {
    public static TaskArtifactResponse from(TaskArtifact artifact) {
        return new TaskArtifactResponse(
                artifact.getId(),
                artifact.getArtifactType(),
                artifact.getName(),
                artifact.getUrl()
        );
    }
}