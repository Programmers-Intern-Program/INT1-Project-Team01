package back.domain.chat.dto.response;

import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.task.entity.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageSendResponse(
        Long chatSessionId,
        Long taskId,
        Long workspaceId,
        Long assignedAgentId,
        TaskStatus taskStatus,
        Long taskExecutionId,
        TaskExecutionStatus executionStatus,
        String finalText,
        String failureReason,
        LocalDateTime createdAt,
        List<ChatMessageResponse> messages) {

    public ChatMessageSendResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
