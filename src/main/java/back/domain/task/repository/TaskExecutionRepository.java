package back.domain.task.repository;

import back.domain.task.domain.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    Optional<TaskExecution> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);
}