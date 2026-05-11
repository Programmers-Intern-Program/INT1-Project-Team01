package back.domain.task.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.task.entity.TaskExecutionLog;

public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    List<TaskExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    List<TaskExecutionLog> findTop5ByExecutionIdInOrderByCreatedAtDescIdDesc(Collection<Long> executionIds);
}
