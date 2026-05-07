package back.domain.agent.service;

import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;

public interface AgentProvisioningService {

    OpenClawAgentCreateRes createAgent(Long workspaceId, Long memberId, OpenClawAgentCreateReq request);
}
