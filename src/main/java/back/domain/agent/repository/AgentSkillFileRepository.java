package back.domain.agent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.agent.entity.AgentSkillFile;

public interface AgentSkillFileRepository extends JpaRepository<AgentSkillFile, Long> {

    List<AgentSkillFile> findByAgentIdOrderByIdAsc(Long agentId);

    List<AgentSkillFile> findByAgentIdInOrderByAgentIdAscIdAsc(List<Long> agentIds);
}
