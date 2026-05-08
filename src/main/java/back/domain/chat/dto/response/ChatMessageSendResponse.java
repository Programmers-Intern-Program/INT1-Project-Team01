package back.domain.chat.dto.response;

import back.domain.execution.entity.TaskExecutionStatus;
import back.domain.task.dto.response.TaskMessageResponse;
import back.domain.task.dto.response.TaskRunResponse;
import back.domain.task.entity.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageSendResponse(
        Long taskId,
        Long workspaceId,
        Long assignedAgentId,
        TaskStatus taskStatus,
        Long taskExecutionId,
        TaskExecutionStatus executionStatus,
        String finalText,
        String failureReason,
        LocalDateTime createdAt,
        List<TaskMessageResponse> messages) {

    public ChatMessageSendResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static ChatMessageSendResponse from(TaskRunResponse runResponse, List<TaskMessageResponse> messages) {
        return new ChatMessageSendResponse(
                runResponse.taskId(),
                runResponse.workspaceId(),
                runResponse.assignedAgentId(),
                runResponse.taskStatus(),
                runResponse.taskExecutionId(),
                runResponse.executionStatus(),
                runResponse.finalText(),
                runResponse.failureReason(),
                runResponse.createdAt(),
                messages);
    }
}
