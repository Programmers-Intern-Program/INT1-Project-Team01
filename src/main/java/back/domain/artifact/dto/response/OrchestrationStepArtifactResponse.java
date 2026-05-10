package back.domain.artifact.dto.response;

import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Immutable response DTO uses list values assembled for API serialization.")
public record OrchestrationStepArtifactResponse(
        Long stepId, String stepKey, int sequenceNo, String title, List<ArtifactFileReferenceResponse> files) {}
