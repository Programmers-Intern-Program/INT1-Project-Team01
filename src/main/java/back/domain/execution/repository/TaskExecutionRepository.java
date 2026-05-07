package back.domain.execution.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.execution.entity.TaskExecution;

import java.util.Optional;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    Optional<TaskExecution> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);
}
