package back.domain.artifact.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import back.domain.artifact.dto.response.ArtifactFileContentResponse;
import back.domain.artifact.dto.response.ArtifactTreeResponse;
import back.domain.artifact.dto.response.OrchestrationArtifactResponse;
import back.domain.artifact.dto.response.OrchestrationStepArtifactResponse;
import back.domain.artifact.service.ArtifactQueryService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/artifacts")
public class ArtifactController {

    private final ArtifactQueryService artifactQueryService;

    @GetMapping("/tree")
    public ResponseEntity<ArtifactTreeResponse> getProjectTree(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(artifactQueryService.getProjectTree(workspaceId));
    }

    @GetMapping("/files")
    public ResponseEntity<ArtifactFileContentResponse> getFileContent(
            @PathVariable Long workspaceId, @RequestParam String path) {
        return ResponseEntity.ok(artifactQueryService.getFileContent(workspaceId, path));
    }

    @GetMapping("/orchestration-plans/{planId}")
    public ResponseEntity<OrchestrationArtifactResponse> getPlanArtifacts(
            @PathVariable Long workspaceId, @PathVariable Long planId) {
        return ResponseEntity.ok(artifactQueryService.getPlanArtifacts(workspaceId, planId));
    }

    @GetMapping("/orchestration-plans/{planId}/steps/{stepId}")
    public ResponseEntity<OrchestrationStepArtifactResponse> getStepArtifacts(
            @PathVariable Long workspaceId, @PathVariable Long planId, @PathVariable Long stepId) {
        return ResponseEntity.ok(artifactQueryService.getStepArtifacts(workspaceId, planId, stepId));
    }
}
