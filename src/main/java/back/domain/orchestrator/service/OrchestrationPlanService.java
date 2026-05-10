package back.domain.orchestrator.service;

import back.domain.orchestrator.dto.request.OrchestrationPlanCreateCommand;
import back.domain.orchestrator.entity.OrchestrationPlan;

public interface OrchestrationPlanService {

    OrchestrationPlan createPlan(OrchestrationPlanCreateCommand command);
}
