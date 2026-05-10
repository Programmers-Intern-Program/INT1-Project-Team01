package back.domain.orchestrator.dto.request;

import java.util.List;

import back.domain.agent.entity.AgentCategory;

public record OrchestrationPlanCreateCommand(
        Long workspaceId,
        Long chatSessionId,
        Long orchestratorAgentId,
        Long userMessageId,
        String title,
        String rawResponse,
        List<StepCommand> steps) {

    public OrchestrationPlanCreateCommand {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public record StepCommand(
            String stepKey,
            Long agentId,
            String agentName,
            AgentCategory category,
            String title,
            String prompt,
            List<String> dependsOn) {

        public StepCommand {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }
}
