package back.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import back.domain.agent.dto.request.AgentSkillFileReq;
import back.domain.agent.entity.AgentCategory;

class AgentSkillTemplateResolverTest {

    private final AgentSkillTemplateResolver agentSkillTemplateResolver = new AgentSkillTemplateResolver();

    @Test
    @DisplayName("Backend category 템플릿은 AGENTS.md에 common과 backend 지침을 병합한다")
    void resolve_backendCategory_returnsMergedAgentsTemplate() {
        // when
        var templates = agentSkillTemplateResolver.resolve(AgentCategory.BACKEND);

        // then
        assertThat(templates)
                .extracting(AgentSkillFileReq::fileName)
                .containsExactly("AGENTS.md");
        assertThat(templates.getFirst().content())
                .contains("# Common Agent Instructions")
                .contains("# Backend Agent Instructions");
    }

    @Test
    @DisplayName("Custom category 템플릿은 AGENTS.md에 common 지침만 담는다")
    void resolve_customCategory_returnsCommonAgentsTemplateOnly() {
        // when
        var templates = agentSkillTemplateResolver.resolve(AgentCategory.CUSTOM);

        // then
        assertThat(templates)
                .extracting(AgentSkillFileReq::fileName)
                .containsExactly("AGENTS.md");
        assertThat(templates.getFirst().content())
                .contains("# Common Agent Instructions")
                .doesNotContain("# Backend Agent Instructions")
                .doesNotContain("# Frontend Agent Instructions")
                .doesNotContain("# QA Agent Instructions")
                .doesNotContain("# Orchestrator Agent Instructions");
    }

    @Test
    @DisplayName("같은 category 템플릿은 캐시된 결과를 반환한다")
    void resolve_sameCategory_returnsCachedTemplates() {
        // when
        var first = agentSkillTemplateResolver.resolve(AgentCategory.BACKEND);
        var second = agentSkillTemplateResolver.resolve(AgentCategory.BACKEND);

        // then
        assertThat(second).isSameAs(first);
    }
}
