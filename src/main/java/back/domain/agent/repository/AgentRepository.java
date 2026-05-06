package back.domain.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.agent.entity.Agent;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);
}
