package back.domain.task.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import back.domain.task.entity.Task;
import back.domain.task.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByWorkspaceId(Long workspaceId, Pageable pageable);

    long countByWorkspaceId(Long workspaceId);

    long countByWorkspaceIdAndStatus(Long workspaceId, TaskStatus status);

    long countByWorkspaceIdAndStatusIn(Long workspaceId, Collection<TaskStatus> statuses);

    @Query("""
            SELECT t.workspaceId AS workspaceId, COUNT(t) AS count
            FROM Task t
            WHERE t.workspaceId IN :workspaceIds
              AND t.status IN :statuses
            GROUP BY t.workspaceId
            """)
    List<WorkspaceCount> countByWorkspaceIdsAndStatusIn(
            @Param("workspaceIds") Collection<Long> workspaceIds,
            @Param("statuses") Collection<TaskStatus> statuses);

    Optional<Task> findByIdAndWorkspaceId(Long taskId, Long workspaceId);

    interface WorkspaceCount {
        Long getWorkspaceId();

        long getCount();
    }
}
