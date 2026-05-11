package back.domain.agent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    boolean existsByWorkspaceIdAndNameAndStatusNot(Long workspaceId, String name, AgentStatus status);

    Optional<Agent> findByWorkspaceIdAndNameAndStatusNot(Long workspaceId, String name, AgentStatus status);

    Optional<Agent> findByIdAndWorkspaceId(Long agentId, Long workspaceId);

    Optional<Agent> findByIdAndWorkspaceIdAndStatusNot(Long agentId, Long workspaceId, AgentStatus status);

    List<Agent> findByWorkspaceIdAndStatusNotOrderByIdAsc(Long workspaceId, AgentStatus status);

    Optional<Agent> findFirstByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
            Long workspaceId, AgentStatus status);

    List<Agent> findByWorkspaceIdAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
            Long workspaceId, AgentStatus status);

    @Query("""
            SELECT a
            FROM Agent a
            WHERE a.workspace.id = :workspaceId
              AND a.status = :status
              AND a.openClawAgentId IS NOT NULL
              AND (
                  a.category = :category
                  OR (:category = back.domain.agent.entity.AgentCategory.CUSTOM AND a.category IS NULL)
              )
            ORDER BY a.id ASC
            """)
    Optional<Agent> findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
            @Param("workspaceId") Long workspaceId,
            @Param("category") AgentCategory category,
            @Param("status") AgentStatus status);
}
