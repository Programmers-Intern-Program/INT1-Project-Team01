package back.domain.artifact.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.artifact.dto.ArtifactFileContent;
import back.domain.artifact.dto.ArtifactFileReference;
import back.domain.artifact.dto.ArtifactTreeNode;
import back.domain.artifact.dto.response.ArtifactFileContentResponse;
import back.domain.artifact.dto.response.ArtifactFileReferenceResponse;
import back.domain.artifact.dto.response.ArtifactTreeNodeResponse;
import back.domain.artifact.dto.response.ArtifactTreeResponse;
import back.domain.artifact.dto.response.OrchestrationArtifactResponse;
import back.domain.artifact.dto.response.OrchestrationStepArtifactResponse;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.entity.OrchestrationPlanStep;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.domain.orchestrator.repository.OrchestrationPlanStepRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed dependencies are injected and retained by this service.")
public class ArtifactQueryServiceImpl implements ArtifactQueryService {

    private static final String ROOT_PATH = ".";

    private final WorkspaceArtifactStorage workspaceArtifactStorage;
    private final OrchestrationPlanRepository orchestrationPlanRepository;
    private final OrchestrationPlanStepRepository orchestrationPlanStepRepository;

    @Override
    public ArtifactTreeResponse getProjectTree(Long workspaceId) {
        List<ArtifactTreeNodeResponse> children =
                workspaceArtifactStorage.listProjectTree(workspaceId).children().stream()
                        .map(this::toTreeNodeResponse)
                        .toList();
        return new ArtifactTreeResponse(workspaceId, ROOT_PATH, children);
    }

    @Override
    public ArtifactFileContentResponse getFileContent(Long workspaceId, String path) {
        ArtifactFileContent file = workspaceArtifactStorage.readFile(workspaceId, path);
        return new ArtifactFileContentResponse(
                workspaceId, file.path(), file.name(), file.contentType(), file.sizeBytes(), file.content());
    }

    @Override
    public OrchestrationArtifactResponse getPlanArtifacts(Long workspaceId, Long planId) {
        OrchestrationPlan plan = findPlan(workspaceId, planId);
        List<OrchestrationStepArtifactResponse> steps =
                orchestrationPlanStepRepository.findByPlanIdOrderBySequenceNoAscIdAsc(plan.getId()).stream()
                        .map(step -> toStepArtifactResponse(workspaceId, step))
                        .toList();
        return new OrchestrationArtifactResponse(workspaceId, plan.getId(), steps);
    }

    @Override
    public OrchestrationStepArtifactResponse getStepArtifacts(Long workspaceId, Long planId, Long stepId) {
        OrchestrationPlan plan = findPlan(workspaceId, planId);
        return orchestrationPlanStepRepository.findByPlanIdOrderBySequenceNoAscIdAsc(plan.getId()).stream()
                .filter(step -> step.getId().equals(stepId))
                .findFirst()
                .map(step -> toStepArtifactResponse(workspaceId, step))
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ArtifactQueryServiceImpl#getStepArtifacts] step not found. stepId=" + stepId,
                        "오케스트레이션 step을 찾을 수 없습니다."));
    }

    private OrchestrationPlan findPlan(Long workspaceId, Long planId) {
        return orchestrationPlanRepository
                .findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[ArtifactQueryServiceImpl#findPlan] plan not found. workspaceId="
                                + workspaceId
                                + ", planId="
                                + planId,
                        "오케스트레이션 플랜을 찾을 수 없습니다."));
    }

    private ArtifactTreeNodeResponse toTreeNodeResponse(ArtifactTreeNode node) {
        return new ArtifactTreeNodeResponse(
                node.name(),
                node.path(),
                node.type(),
                node.contentType(),
                node.sizeBytes(),
                node.children().stream().map(this::toTreeNodeResponse).toList());
    }

    private OrchestrationStepArtifactResponse toStepArtifactResponse(Long workspaceId, OrchestrationPlanStep step) {
        return new OrchestrationStepArtifactResponse(
                step.getId(),
                step.getStepKey(),
                step.getSequenceNo(),
                step.getTitle(),
                parseFilePaths(step.getResultFilePaths()).stream()
                        .map(path -> toFileReferenceResponse(workspaceId, path))
                        .toList());
    }

    private ArtifactFileReferenceResponse toFileReferenceResponse(Long workspaceId, String path) {
        ArtifactFileReference file = workspaceArtifactStorage.describeFile(workspaceId, path);
        return new ArtifactFileReferenceResponse(
                file.path(), file.name(), file.contentType(), file.sizeBytes(), file.exists());
    }

    private List<String> parseFilePaths(String filePaths) {
        if (filePaths == null || filePaths.isBlank()) {
            return List.of();
        }
        Set<String> uniquePaths = Arrays.stream(filePaths.split("\\R"))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(uniquePaths);
    }
}
