package back.domain.agent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import back.domain.agent.dto.request.AgentSkillFileReq;
import back.domain.agent.entity.AgentCategory;

@Component
public class AgentSkillTemplateResolver {

    private static final List<TemplateResource> COMMON_TEMPLATES =
            List.of(new TemplateResource("COMMON.md", "agent-templates/common/COMMON.md"));

    private static final Map<AgentCategory, List<TemplateResource>> CATEGORY_TEMPLATES = Map.of(
            AgentCategory.ORCHESTRATOR,
            List.of(new TemplateResource("ORCHESTRATOR.md", "agent-templates/orchestrator/ORCHESTRATOR.md")),
            AgentCategory.BACKEND,
            List.of(new TemplateResource("BACKEND.md", "agent-templates/backend/BACKEND.md")),
            AgentCategory.FRONTEND,
            List.of(new TemplateResource("FRONTEND.md", "agent-templates/frontend/FRONTEND.md")),
            AgentCategory.QA,
            List.of(new TemplateResource("QA.md", "agent-templates/qa/QA.md")));

    public List<AgentSkillFileReq> resolve(AgentCategory category) {
        AgentCategory resolvedCategory = category == null ? AgentCategory.CUSTOM : category;
        List<TemplateResource> categoryTemplates = CATEGORY_TEMPLATES.getOrDefault(resolvedCategory, List.of());

        return Stream.concat(COMMON_TEMPLATES.stream(), categoryTemplates.stream())
                .map(this::readTemplate)
                .toList();
    }

    private AgentSkillFileReq readTemplate(TemplateResource template) {
        ClassPathResource resource = new ClassPathResource(template.path());
        if (!resource.exists()) {
            throw new IllegalStateException("Agent skill template not found: " + template.path());
        }
        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return new AgentSkillFileReq(template.fileName(), content);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read agent skill template: " + template.path(), exception);
        }
    }

    private record TemplateResource(String fileName, String path) {}
}
