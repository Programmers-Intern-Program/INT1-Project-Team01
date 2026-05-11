package back.domain.agent.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.agent.dto.response.AgentInfoRes;
import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentSkillFile;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.agent.repository.AgentSkillFileRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed dependencies are injected and retained by this service.")
public class AgentQueryServiceImpl implements AgentQueryService {

    private final WorkspaceAccessValidator workspaceAccessValidator;
    private final AgentRepository agentRepository;
    private final AgentSkillFileRepository agentSkillFileRepository;

    @Override
    public List<AgentInfoRes> listAgents(Long workspaceId, Long memberId) {
        workspaceAccessValidator.requireMember(workspaceId, memberId);
        List<Agent> agents = agentRepository.findByWorkspaceIdAndStatusNotOrderByIdAsc(
                workspaceId,
                AgentStatus.DISABLED);
        Map<Long, List<AgentSkillFile>> skillFilesByAgentId = findSkillFilesByAgentId(agents);
        return agents.stream()
                .map(agent -> AgentInfoRes.from(agent, skillFilesByAgentId.getOrDefault(agent.getId(), List.of())))
                .toList();
    }

    @Override
    public AgentInfoRes getAgent(Long workspaceId, Long memberId, Long agentId) {
        workspaceAccessValidator.requireMember(workspaceId, memberId);
        Agent agent = agentRepository
                .findByIdAndWorkspaceIdAndStatusNot(agentId, workspaceId, AgentStatus.DISABLED)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[AgentQueryServiceImpl#getAgent] agent not found. agentId=" + agentId
                                + ", workspaceId="
                                + workspaceId,
                        "Agent를 찾을 수 없습니다."));
        List<AgentSkillFile> skillFiles = agentSkillFileRepository.findByAgentIdOrderByIdAsc(agentId);
        return AgentInfoRes.from(agent, skillFiles);
    }

    private Map<Long, List<AgentSkillFile>> findSkillFilesByAgentId(List<Agent> agents) {
        List<Long> agentIds = agents.stream().map(Agent::getId).toList();
        if (agentIds.isEmpty()) {
            return Map.of();
        }
        return agentSkillFileRepository.findByAgentIdInOrderByAgentIdAscIdAsc(agentIds).stream()
                .collect(Collectors.groupingBy(skillFile -> skillFile.getAgent().getId()));
    }
}
