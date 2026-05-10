package back.domain.orchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestrationPlanDispatcher {

    private final OrchestrationPlanRunner orchestrationPlanRunner;

    @Async("taskExecutionTaskExecutor")
    public void run(Long workspaceId, Long planId) {
        try {
            orchestrationPlanRunner.run(workspaceId, planId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Orchestration plan execution failed. workspaceId={}, planId={}",
                    workspaceId,
                    planId,
                    exception);
        }
    }
}
