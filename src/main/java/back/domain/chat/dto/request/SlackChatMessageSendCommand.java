package back.domain.chat.dto.request;

import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;

public record SlackChatMessageSendCommand(
        String sourceRef,
        String message,
        String agentName,
        Long agentId,
        Long repositoryId,
        TaskType taskType,
        TaskPriority priority,
        String title,
        Boolean createPr) {

    public SlackChatMessageSendCommand(String sourceRef, String message) {
        this(sourceRef, message, null);
    }

    public SlackChatMessageSendCommand(String sourceRef, String message, String agentName) {
        this(sourceRef, message, agentName, null, null, null, null, null, false);
    }
}
