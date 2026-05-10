package back.domain.orchestrator.service;

public interface OrchestrationPlanRunner {

    void run(Long workspaceId, Long planId);
}
