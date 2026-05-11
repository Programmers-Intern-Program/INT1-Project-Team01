package back.domain.agent.service;

public interface AgentDeletionService {

    void deleteAgent(Long workspaceId, Long memberId, Long agentId);
}
