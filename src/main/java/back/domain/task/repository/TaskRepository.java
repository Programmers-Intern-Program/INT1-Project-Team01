package back.domain.task.repository;

import back.domain.task.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByWorkspaceId(Long workspaceId, Pageable pageable);

    Optional<Task> findByIdAndWorkspaceId(Long taskId, Long workspaceId);
}
