package back.domain.artifact.service;

import back.domain.artifact.dto.response.ArtifactFileContentResponse;
import back.domain.artifact.dto.response.ArtifactTreeResponse;
import back.domain.artifact.dto.response.OrchestrationArtifactResponse;
import back.domain.artifact.dto.response.OrchestrationStepArtifactResponse;

public interface ArtifactQueryService {

    ArtifactTreeResponse getProjectTree(Long workspaceId);

    ArtifactFileContentResponse getFileContent(Long workspaceId, String path);

    OrchestrationArtifactResponse getPlanArtifacts(Long workspaceId, Long planId);

    OrchestrationStepArtifactResponse getStepArtifacts(Long workspaceId, Long planId, Long stepId);
}
