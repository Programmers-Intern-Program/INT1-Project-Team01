package back.domain.agent.dto.request;

import java.util.List;

import back.domain.agent.entity.AgentCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record OpenClawAgentCreateReq(
        @NotBlank String name,
        AgentCategory category,
        String workspacePath,
        String emoji,
        @Valid List<AgentSkillFileReq> skillFiles) {

    public OpenClawAgentCreateReq {
        category = category == null ? AgentCategory.CUSTOM : category;
        skillFiles = skillFiles == null ? List.of() : List.copyOf(skillFiles);
    }

    public OpenClawAgentCreateReq(
            String name,
            String workspacePath,
            String emoji,
            List<AgentSkillFileReq> skillFiles) {
        this(name, AgentCategory.CUSTOM, workspacePath, emoji, skillFiles);
    }
}
