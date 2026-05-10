package back.domain.artifact.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import back.domain.artifact.dto.ArtifactTreeNodeType;
import back.domain.artifact.dto.response.ArtifactFileContentResponse;
import back.domain.artifact.dto.response.ArtifactFileReferenceResponse;
import back.domain.artifact.dto.response.ArtifactTreeNodeResponse;
import back.domain.artifact.dto.response.ArtifactTreeResponse;
import back.domain.artifact.dto.response.OrchestrationArtifactResponse;
import back.domain.artifact.dto.response.OrchestrationStepArtifactResponse;
import back.domain.artifact.service.ArtifactQueryService;
import back.testUtil.WebMvcTestSupport;

@WebMvcTest(ArtifactController.class)
class ArtifactControllerTest extends WebMvcTestSupport {

    @MockitoBean
    private ArtifactQueryService artifactQueryService;

    @Test
    @DisplayName("project root 파일 트리를 조회한다")
    void getProjectTree_success() throws Exception {
        // given
        given(artifactQueryService.getProjectTree(1L))
                .willReturn(new ArtifactTreeResponse(
                        1L,
                        ".",
                        List.of(new ArtifactTreeNodeResponse(
                                "README.md", "README.md", ArtifactTreeNodeType.FILE, "text/markdown", 9L, List.of()))));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/artifacts/tree", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(1L))
                .andExpect(jsonPath("$.rootPath").value("."))
                .andExpect(jsonPath("$.children[0].path").value("README.md"))
                .andExpect(jsonPath("$.children[0].contentType").value("text/markdown"));
    }

    @Test
    @DisplayName("산출물 파일 내용을 조회한다")
    void getFileContent_success() throws Exception {
        // given
        given(artifactQueryService.getFileContent(1L, "README.md"))
                .willReturn(new ArtifactFileContentResponse(
                        1L, "README.md", "README.md", "text/markdown", 9L, "# Result\n"));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/artifacts/files", 1L)
                        .queryParam("path", "README.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(1L))
                .andExpect(jsonPath("$.path").value("README.md"))
                .andExpect(jsonPath("$.contentType").value("text/markdown"))
                .andExpect(jsonPath("$.content").value("# Result\n"));
    }

    @Test
    @DisplayName("오케스트레이션 플랜 산출물 목록을 조회한다")
    void getPlanArtifacts_success() throws Exception {
        // given
        given(artifactQueryService.getPlanArtifacts(1L, 10L))
                .willReturn(new OrchestrationArtifactResponse(
                        1L,
                        10L,
                        List.of(new OrchestrationStepArtifactResponse(
                                20L,
                                "backend-1",
                                1,
                                "백엔드 구현",
                                List.of(new ArtifactFileReferenceResponse(
                                        "README.md", "README.md", "text/markdown", 9L, true))))));

        // when & then
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/artifacts/orchestration-plans/{planId}", 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(1L))
                .andExpect(jsonPath("$.planId").value(10L))
                .andExpect(jsonPath("$.steps[0].stepId").value(20L))
                .andExpect(jsonPath("$.steps[0].files[0].path").value("README.md"));
    }

    @Test
    @DisplayName("오케스트레이션 step 산출물 목록을 조회한다")
    void getStepArtifacts_success() throws Exception {
        // given
        given(artifactQueryService.getStepArtifacts(1L, 10L, 20L))
                .willReturn(new OrchestrationStepArtifactResponse(
                        20L,
                        "backend-1",
                        1,
                        "백엔드 구현",
                        List.of(new ArtifactFileReferenceResponse(
                                "src/main/java/App.java", "App.java", "text/x-java-source", 13L, true))));

        // when & then
        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/artifacts/orchestration-plans/{planId}/steps/{stepId}",
                        1L,
                        10L,
                        20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepId").value(20L))
                .andExpect(jsonPath("$.stepKey").value("backend-1"))
                .andExpect(jsonPath("$.files[0].contentType").value("text/x-java-source"));
    }
}
