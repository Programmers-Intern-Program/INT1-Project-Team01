package back.domain.chat.service;

import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;

public record ChatAgentIntent(
        ChatAgentIntentType type,
        String message,
        TaskSpec task) {

    public static ChatAgentIntent chat(String message) {
        return new ChatAgentIntent(ChatAgentIntentType.CHAT, message, null);
    }

    public static ChatAgentIntent task(String message, TaskSpec task) {
        return new ChatAgentIntent(ChatAgentIntentType.TASK, message, task);
    }

    public boolean isTask() {
        return type == ChatAgentIntentType.TASK;
    }

    public record TaskSpec(
            String title,
            String description,
            TaskType taskType,
            TaskPriority priority,
            Long repositoryId,
            Boolean createPr) {}
}
