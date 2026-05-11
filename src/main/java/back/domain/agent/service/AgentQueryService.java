package back.domain.agent.service;

import java.util.List;

import back.domain.agent.dto.response.AgentInfoRes;

public interface AgentQueryService {

    List<AgentInfoRes> listAgents(Long workspaceId, Long memberId);

    AgentInfoRes getAgent(Long workspaceId, Long memberId, Long agentId);
}
