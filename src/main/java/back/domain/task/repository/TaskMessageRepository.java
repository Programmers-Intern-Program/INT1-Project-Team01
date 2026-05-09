package back.domain.task.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.task.entity.TaskMessage;
import back.domain.task.entity.TaskMessageRole;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    List<TaskMessage> findByWorkspaceIdAndTaskIdOrderByCreatedAtAscIdAsc(Long workspaceId, Long taskId);

    Optional<TaskMessage> findFirstByWorkspaceIdAndTaskIdAndTaskExecutionIdAndRoleOrderByCreatedAtDescIdDesc(
            Long workspaceId,
            Long taskId,
            Long taskExecutionId,
            TaskMessageRole role);
}
