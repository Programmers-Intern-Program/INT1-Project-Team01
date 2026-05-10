package back.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import back.domain.agent.dto.request.AgentSkillFileReq;
import back.domain.agent.entity.AgentCategory;

class AgentSkillTemplateResolverTest {

    private final AgentSkillTemplateResolver agentSkillTemplateResolver = new AgentSkillTemplateResolver();

    @Test
    @DisplayName("Backend category 템플릿은 common과 backend MD 파일을 반환한다")
    void resolve_backendCategory_returnsCommonAndBackendTemplates() {
        // when
        var templates = agentSkillTemplateResolver.resolve(AgentCategory.BACKEND);

        // then
        assertThat(templates)
                .extracting(AgentSkillFileReq::fileName)
                .containsExactly("COMMON.md", "BACKEND.md");
        assertThat(templates)
                .extracting(AgentSkillFileReq::content)
                .allSatisfy(content -> assertThat(content).isNotBlank());
    }

    @Test
    @DisplayName("Custom category 템플릿은 common MD 파일만 반환한다")
    void resolve_customCategory_returnsCommonTemplateOnly() {
        // when
        var templates = agentSkillTemplateResolver.resolve(AgentCategory.CUSTOM);

        // then
        assertThat(templates)
                .extracting(AgentSkillFileReq::fileName)
                .containsExactly("COMMON.md");
    }
}
