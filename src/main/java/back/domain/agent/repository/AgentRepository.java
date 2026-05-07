package back.domain.agent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentStatus;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);

    Optional<Agent> findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
            Long workspaceId, AgentStatus status);
}
