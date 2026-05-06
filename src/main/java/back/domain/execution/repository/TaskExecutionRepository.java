package back.domain.execution.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.execution.entity.TaskExecution;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {}
