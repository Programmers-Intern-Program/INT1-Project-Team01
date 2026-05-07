package back.domain.task.dto.response;

import back.domain.execution.entity.ExecutionTaskArtifact;
import back.domain.task.entity.ArtifactType;
import back.domain.task.entity.TaskArtifact;
import java.util.Locale;

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

    public static TaskArtifactResponse from(ExecutionTaskArtifact artifact) {
        return new TaskArtifactResponse(
                artifact.getId(),
                toArtifactType(artifact.getArtifactType()),
                artifact.getName(),
                artifact.getUrl()
        );
    }

    private static ArtifactType toArtifactType(String value) {
        if (value == null || value.isBlank()) {
            return ArtifactType.OTHER;
        }
        try {
            return ArtifactType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ArtifactType.OTHER;
        }
    }
}
