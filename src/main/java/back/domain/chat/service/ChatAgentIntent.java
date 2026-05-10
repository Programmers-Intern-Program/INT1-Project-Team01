package back.domain.chat.service;

import java.util.List;

import back.domain.agent.entity.AgentCategory;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;

public record ChatAgentIntent(
        ChatAgentIntentType type,
        String message,
        TaskSpec task,
        OrchestrationPlanSpec orchestrationPlan) {

    public static ChatAgentIntent chat(String message) {
        return new ChatAgentIntent(ChatAgentIntentType.CHAT, message, null, null);
    }

    public static ChatAgentIntent task(String message, TaskSpec task) {
        return new ChatAgentIntent(ChatAgentIntentType.TASK, message, task, null);
    }

    public static ChatAgentIntent orchestrate(String message, OrchestrationPlanSpec orchestrationPlan) {
        return new ChatAgentIntent(ChatAgentIntentType.ORCHESTRATE, message, null, orchestrationPlan);
    }

    public boolean isTask() {
        return type == ChatAgentIntentType.TASK;
    }

    public boolean isOrchestration() {
        return type == ChatAgentIntentType.ORCHESTRATE;
    }

    public record TaskSpec(
            String title,
            String description,
            TaskType taskType,
            TaskPriority priority,
            Long repositoryId,
            Boolean createPr) {}

    public record OrchestrationPlanSpec(
            String title,
            List<OrchestrationStepSpec> steps) {

        public OrchestrationPlanSpec {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    public record OrchestrationStepSpec(
            String stepKey,
            Long agentId,
            String agentName,
            AgentCategory category,
            String title,
            String prompt,
            List<String> dependsOn) {

        public OrchestrationStepSpec {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }
}
