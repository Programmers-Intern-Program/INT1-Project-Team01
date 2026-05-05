package back.domain.task.repository;

import back.domain.task.domain.TaskExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    List<TaskExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);
}