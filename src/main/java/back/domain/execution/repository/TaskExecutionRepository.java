package back.domain.execution.repository;

import java.util.List;
import java.util.Optional;

import back.domain.execution.entity.TaskExecution;
import back.domain.execution.entity.TaskExecutionStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    Optional<TaskExecution> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);

    @Query("""
            SELECT COUNT(DISTINCT te.agentId)
            FROM TaskExecution te
            WHERE te.workspaceId = :workspaceId
              AND te.status = :status
            """)
    long countRunningAgentCount(
            @Param("workspaceId") Long workspaceId,
            @Param("status") TaskExecutionStatus status);

    List<TaskExecution> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);
}
