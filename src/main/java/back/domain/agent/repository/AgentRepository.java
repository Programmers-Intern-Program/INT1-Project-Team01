package back.domain.agent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);

    Optional<Agent> findByWorkspaceIdAndName(Long workspaceId, String name);

    Optional<Agent> findByIdAndWorkspaceId(Long agentId, Long workspaceId);

    Optional<Agent> findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
            Long workspaceId, AgentStatus status);

    Optional<Agent> findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
            Long workspaceId, AgentCategory category, AgentStatus status);
}
