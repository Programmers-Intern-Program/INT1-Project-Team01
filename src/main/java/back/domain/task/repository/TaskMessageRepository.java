package back.domain.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.task.entity.TaskMessage;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    List<TaskMessage> findByWorkspaceIdAndTaskIdOrderByCreatedAtAscIdAsc(Long workspaceId, Long taskId);
}
