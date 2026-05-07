package back.domain.execution.repository;

import back.domain.execution.entity.ExecutionAgentReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionAgentReportRepository extends JpaRepository<ExecutionAgentReport, Long> {

    Optional<ExecutionAgentReport> findByTaskExecutionId(Long taskExecutionId);
}
