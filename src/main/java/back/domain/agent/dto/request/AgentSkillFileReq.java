package back.domain.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentSkillFileReq(@NotBlank String fileName, @NotNull String content) {}
