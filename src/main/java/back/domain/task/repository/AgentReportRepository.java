package back.domain.task.repository;

import back.domain.task.entity.AgentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentReportRepository extends JpaRepository<AgentReport, Long> {

    List<AgentReport> findByTaskIdOrderByCreatedAtDesc(Long taskId);
}