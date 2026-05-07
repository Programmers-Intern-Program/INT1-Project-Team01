package back.domain.orchestrator.repository;

import back.domain.orchestrator.entity.OrchestratorSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchestratorSessionRepository extends JpaRepository<OrchestratorSession, Long> {
}