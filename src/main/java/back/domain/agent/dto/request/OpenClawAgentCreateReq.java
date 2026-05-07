package back.domain.agent.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record OpenClawAgentCreateReq(
        @NotBlank String name, String workspacePath, String emoji, @Valid List<AgentSkillFileReq> skillFiles) {

    public OpenClawAgentCreateReq {
        skillFiles = skillFiles == null ? List.of() : List.copyOf(skillFiles);
    }
}
