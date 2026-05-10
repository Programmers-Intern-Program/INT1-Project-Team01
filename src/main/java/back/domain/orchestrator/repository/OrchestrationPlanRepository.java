package back.domain.orchestrator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.orchestrator.entity.OrchestrationPlan;

public interface OrchestrationPlanRepository extends JpaRepository<OrchestrationPlan, Long> {

    Optional<OrchestrationPlan> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<OrchestrationPlan> findByWorkspaceIdAndChatSessionIdOrderByCreatedAtDescIdDesc(
            Long workspaceId, Long chatSessionId);
}
