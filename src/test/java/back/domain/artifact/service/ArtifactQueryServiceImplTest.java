package back.domain.artifact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.agent.entity.AgentCategory;
import back.domain.artifact.dto.ArtifactFileContent;
import back.domain.artifact.dto.ArtifactFileReference;
import back.domain.artifact.dto.ArtifactTree;
import back.domain.artifact.dto.ArtifactTreeNode;
import back.domain.artifact.dto.ArtifactTreeNodeType;
import back.domain.artifact.dto.response.ArtifactFileContentResponse;
import back.domain.artifact.dto.response.ArtifactTreeResponse;
import back.domain.artifact.dto.response.OrchestrationArtifactResponse;
import back.domain.artifact.dto.response.OrchestrationStepArtifactResponse;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.entity.OrchestrationPlanStep;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.domain.orchestrator.repository.OrchestrationPlanStepRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ArtifactQueryServiceImplTest {

    @Mock
    private WorkspaceArtifactStorage workspaceArtifactStorage;

    @Mock
    private OrchestrationPlanRepository orchestrationPlanRepository;

    @Mock
    private OrchestrationPlanStepRepository orchestrationPlanStepRepository;

    private ArtifactQueryServiceImpl artifactQueryService;

    @BeforeEach
    void setUp() {
        artifactQueryService = new ArtifactQueryServiceImpl(
                workspaceArtifactStorage, orchestrationPlanRepository, orchestrationPlanStepRepository);
    }

    @Test
    @DisplayName("project root 파일 트리를 응답 DTO로 변환한다")
    void getProjectTree_success() {
        // given
        ArtifactTree tree = new ArtifactTree(List.of(new ArtifactTreeNode(
                "README.md", "README.md", ArtifactTreeNodeType.FILE, "text/markdown", 9L, List.of())));
        given(workspaceArtifactStorage.listProjectTree(1L)).willReturn(tree);

        // when
        ArtifactTreeResponse response = artifactQueryService.getProjectTree(1L);

        // then
        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.rootPath()).isEqualTo(".");
        assertThat(response.children()).hasSize(1);
        assertThat(response.children().getFirst().path()).isEqualTo("README.md");
    }

    @Test
    @DisplayName("산출물 파일 내용을 응답 DTO로 변환한다")
    void getFileContent_success() {
        // given
        given(workspaceArtifactStorage.readFile(1L, "README.md"))
                .willReturn(new ArtifactFileContent("README.md", "README.md", "text/markdown", 9L, "# Result\n"));

        // when
        ArtifactFileContentResponse response = artifactQueryService.getFileContent(1L, "README.md");

        // then
        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.path()).isEqualTo("README.md");
        assertThat(response.contentType()).isEqualTo("text/markdown");
        assertThat(response.content()).isEqualTo("# Result\n");
    }

    @Test
    @DisplayName("오케스트레이션 플랜의 step별 산출물 목록을 조회한다")
    void getPlanArtifacts_success() {
        // given
        OrchestrationPlan plan = createPlan(10L);
        OrchestrationPlanStep step = createCompletedStep(plan, 20L);
        given(orchestrationPlanRepository.findByIdAndWorkspaceId(10L, 1L)).willReturn(Optional.of(plan));
        given(orchestrationPlanStepRepository.findByPlanIdOrderBySequenceNoAscIdAsc(10L))
                .willReturn(List.of(step));
        given(workspaceArtifactStorage.describeFile(1L, "README.md"))
                .willReturn(new ArtifactFileReference("README.md", "README.md", "text/markdown", 9L, true));
        given(workspaceArtifactStorage.describeFile(1L, "src/main/java/App.java"))
                .willReturn(new ArtifactFileReference(
                        "src/main/java/App.java", "App.java", "text/x-java-source", 13L, true));

        // when
        OrchestrationArtifactResponse response = artifactQueryService.getPlanArtifacts(1L, 10L);

        // then
        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.planId()).isEqualTo(10L);
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().getFirst().files())
                .extracting("path")
                .containsExactly("README.md", "src/main/java/App.java");
    }

    @Test
    @DisplayName("특정 step 산출물은 플랜과 워크스페이스 범위를 확인한 뒤 조회한다")
    void getStepArtifacts_success() {
        // given
        OrchestrationPlan plan = createPlan(10L);
        OrchestrationPlanStep step = createCompletedStep(plan, 20L);
        given(orchestrationPlanRepository.findByIdAndWorkspaceId(10L, 1L)).willReturn(Optional.of(plan));
        given(orchestrationPlanStepRepository.findByPlanIdOrderBySequenceNoAscIdAsc(10L))
                .willReturn(List.of(step));
        given(workspaceArtifactStorage.describeFile(1L, "README.md"))
                .willReturn(new ArtifactFileReference("README.md", "README.md", "text/markdown", 9L, true));
        given(workspaceArtifactStorage.describeFile(1L, "src/main/java/App.java"))
                .willReturn(new ArtifactFileReference(
                        "src/main/java/App.java", "App.java", "text/x-java-source", 13L, true));

        // when
        OrchestrationStepArtifactResponse response = artifactQueryService.getStepArtifacts(1L, 10L, 20L);

        // then
        assertThat(response.stepId()).isEqualTo(20L);
        assertThat(response.stepKey()).isEqualTo("backend-1");
        assertThat(response.files()).hasSize(2);
    }

    @Test
    @DisplayName("다른 워크스페이스의 플랜은 조회하지 않는다")
    void getPlanArtifacts_planNotFound_throwsException() {
        // given
        given(orchestrationPlanRepository.findByIdAndWorkspaceId(10L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> artifactQueryService.getPlanArtifacts(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    private OrchestrationPlan createPlan(Long planId) {
        OrchestrationPlan plan = OrchestrationPlan.create(1L, 2L, 3L, 4L, "회원가입 구현 계획", "{\"intent\":\"ORCHESTRATE\"}");
        ReflectionTestUtils.setField(plan, "id", planId);
        return plan;
    }

    private OrchestrationPlanStep createCompletedStep(OrchestrationPlan plan, Long stepId) {
        OrchestrationPlanStep step = OrchestrationPlanStep.create(
                plan,
                1,
                "backend-1",
                5L,
                "backend-agent",
                AgentCategory.BACKEND,
                "백엔드 API 구현",
                "백엔드 API를 구현하세요.",
                List.of());
        ReflectionTestUtils.setField(step, "id", stepId);
        step.markCompleted(
                "COMPLETED",
                "구현 완료",
                "파일을 생성했습니다.",
                List.of("README.md", "src/main/java/App.java", "README.md"),
                List.of(),
                List.of(),
                "완료");
        return step;
    }
}
