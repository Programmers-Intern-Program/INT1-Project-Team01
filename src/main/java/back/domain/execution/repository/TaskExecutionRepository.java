package back.domain.execution.repository;

import back.domain.execution.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    Optional<TaskExecution> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);
}
