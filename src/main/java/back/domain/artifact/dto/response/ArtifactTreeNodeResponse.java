package back.domain.artifact.dto.response;

import java.util.List;

import back.domain.artifact.dto.ArtifactTreeNodeType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Immutable response DTO uses nested list values assembled for API serialization.")
public record ArtifactTreeNodeResponse(
        String name,
        String path,
        ArtifactTreeNodeType type,
        String contentType,
        Long sizeBytes,
        List<ArtifactTreeNodeResponse> children) {}
