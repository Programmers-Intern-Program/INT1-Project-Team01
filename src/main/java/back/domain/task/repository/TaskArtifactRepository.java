package back.domain.task.repository;

import back.domain.task.entity.TaskArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskArtifactRepository extends JpaRepository<TaskArtifact, Long> {

    List<TaskArtifact> findByReportId(Long reportId);
    List<TaskArtifact> findByReportIdIn(List<Long> reportIds);
}