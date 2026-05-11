package back.domain.agent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import back.domain.agent.dto.request.AgentSkillFileReq;
import back.domain.agent.entity.AgentCategory;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;

@Component
public class AgentSkillTemplateResolver {

    private static final String OPENCLAW_AGENT_FILE_NAME = "AGENTS.md";

    private static final List<TemplateResource> COMMON_TEMPLATES =
            List.of(new TemplateResource("agent-templates/common/COMMON.md"));

    private static final Map<AgentCategory, List<TemplateResource>> CATEGORY_TEMPLATES = Map.of(
            AgentCategory.ORCHESTRATOR,
            List.of(new TemplateResource("agent-templates/orchestrator/ORCHESTRATOR.md")),
            AgentCategory.BACKEND, List.of(new TemplateResource("agent-templates/backend/BACKEND.md")),
            AgentCategory.FRONTEND, List.of(new TemplateResource("agent-templates/frontend/FRONTEND.md")),
            AgentCategory.QA, List.of(new TemplateResource("agent-templates/qa/QA.md")));

    private final Map<AgentCategory, List<AgentSkillFileReq>> templatesByCategory;

    public AgentSkillTemplateResolver() {
        this.templatesByCategory = loadTemplatesByCategory();
    }

    public List<AgentSkillFileReq> resolve(AgentCategory category) {
        AgentCategory resolvedCategory = category == null ? AgentCategory.CUSTOM : category;
        return templatesByCategory.getOrDefault(resolvedCategory, templatesByCategory.get(AgentCategory.CUSTOM));
    }

    private Map<AgentCategory, List<AgentSkillFileReq>> loadTemplatesByCategory() {
        Map<AgentCategory, List<AgentSkillFileReq>> templates = new EnumMap<>(AgentCategory.class);
        for (AgentCategory category : AgentCategory.values()) {
            List<TemplateResource> categoryTemplates = CATEGORY_TEMPLATES.getOrDefault(category, List.of());
            String content = Stream.concat(COMMON_TEMPLATES.stream(), categoryTemplates.stream())
                    .map(this::readTemplate)
                    .collect(Collectors.joining("\n\n"));
            templates.put(category, List.of(new AgentSkillFileReq(OPENCLAW_AGENT_FILE_NAME, content)));
        }
        return Map.copyOf(templates);
    }

    private String readTemplate(TemplateResource template) {
        ClassPathResource resource = new ClassPathResource(template.path());
        if (!resource.exists()) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[AgentSkillTemplateResolver#readTemplate] template not found: " + template.path(),
                    "Agent Skill 템플릿을 찾지 못했습니다.");
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[AgentSkillTemplateResolver#readTemplate] failed to read template: "
                            + template.path()
                            + " cause="
                            + exception.getMessage(),
                    "Agent Skill 템플릿을 읽지 못했습니다.");
        }
    }

    private record TemplateResource(String path) {}
}
