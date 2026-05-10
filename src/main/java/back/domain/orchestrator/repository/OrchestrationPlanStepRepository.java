package back.domain.orchestrator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.orchestrator.entity.OrchestrationPlanStep;

public interface OrchestrationPlanStepRepository extends JpaRepository<OrchestrationPlanStep, Long> {

    List<OrchestrationPlanStep> findByPlanIdOrderBySequenceNoAscIdAsc(Long planId);
}
