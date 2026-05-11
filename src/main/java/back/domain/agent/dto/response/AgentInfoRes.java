package back.domain.agent.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.entity.AgentStatus;

public record AgentInfoRes(
        Long agentId,
        Long workspaceId,
        String name,
        AgentCategory category,
        String openClawAgentId,
        String workspacePath,
        AgentStatus status,
        String syncError,
        Long createdByMemberId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AgentSkillFileSyncRes> skillFiles) {

    public AgentInfoRes {
        skillFiles = skillFiles == null ? List.of() : List.copyOf(skillFiles);
    }

    public static AgentInfoRes from(Agent agent, List<AgentSkillFile> skillFiles) {
        return new AgentInfoRes(
                agent.getId(),
                agent.getWorkspace().getId(),
                agent.getName(),
                agent.getCategory(),
                agent.getOpenClawAgentId(),
                agent.getWorkspacePath(),
                agent.getStatus(),
                agent.getSyncError(),
                agent.getCreatedByMemberId(),
                agent.getCreatedAt(),
                agent.getUpdatedAt(),
                skillFiles.stream().map(AgentSkillFileSyncRes::from).toList());
    }
}
