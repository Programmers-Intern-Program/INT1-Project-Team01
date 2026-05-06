package back.domain.agent.dto.response;

import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.entity.AgentSkillSyncStatus;

public record AgentSkillFileSyncRes(Long id, String fileName, AgentSkillSyncStatus syncStatus, String syncError) {

    public static AgentSkillFileSyncRes from(AgentSkillFile skillFile) {
        return new AgentSkillFileSyncRes(
                skillFile.getId(), skillFile.getFileName(), skillFile.getSyncStatus(), skillFile.getSyncError());
    }
}
