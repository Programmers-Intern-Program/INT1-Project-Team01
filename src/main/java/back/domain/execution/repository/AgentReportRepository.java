package back.domain.execution.repository;

import back.domain.execution.entity.AgentReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentReportRepository extends JpaRepository<AgentReport, Long> {

    Optional<AgentReport> findByTaskExecutionId(Long taskExecutionId);
}
