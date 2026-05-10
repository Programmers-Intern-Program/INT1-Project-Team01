package back.domain.artifact.dto;

import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Immutable response DTO uses nested list values assembled for API serialization.")
public record ArtifactTreeNode(
        String name,
        String path,
        ArtifactTreeNodeType type,
        String contentType,
        Long sizeBytes,
        List<ArtifactTreeNode> children) {}
