package back.domain.execution.repository;

import back.domain.execution.entity.TaskArtifact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskArtifactRepository extends JpaRepository<TaskArtifact, Long> {

    List<TaskArtifact> findAllByTaskExecutionIdOrderByIdAsc(Long taskExecutionId);
}
