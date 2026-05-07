package back.domain.execution.repository;

import back.domain.execution.entity.ExecutionTaskArtifact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionTaskArtifactRepository extends JpaRepository<ExecutionTaskArtifact, Long> {

    List<ExecutionTaskArtifact> findAllByTaskExecutionIdOrderByIdAsc(Long taskExecutionId);
}
