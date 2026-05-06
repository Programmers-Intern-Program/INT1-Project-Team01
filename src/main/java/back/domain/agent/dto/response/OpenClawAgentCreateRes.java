package back.domain.agent.dto.response;

import java.util.List;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.entity.AgentStatus;

public record OpenClawAgentCreateRes(
        Long agentId,
        Long workspaceId,
        String name,
        String openClawAgentId,
        String workspacePath,
        AgentStatus status,
        String syncError,
        List<AgentSkillFileSyncRes> skillFiles) {

    public OpenClawAgentCreateRes {
        skillFiles = skillFiles == null ? List.of() : List.copyOf(skillFiles);
    }

    public static OpenClawAgentCreateRes from(Agent agent, List<AgentSkillFile> skillFiles) {
        return new OpenClawAgentCreateRes(
                agent.getId(),
                agent.getWorkspace().getId(),
                agent.getName(),
                agent.getOpenClawAgentId(),
                agent.getWorkspacePath(),
                agent.getStatus(),
                agent.getSyncError(),
                skillFiles.stream().map(AgentSkillFileSyncRes::from).toList());
    }
}
