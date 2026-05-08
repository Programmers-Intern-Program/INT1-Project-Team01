package back.domain.task.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import back.domain.task.entity.TaskMessage;
import back.domain.task.entity.TaskMessageRole;
import back.domain.task.entity.TaskStatus;

public record TaskMessageResponse(
        Long messageId,
        Long taskId,
        Long taskExecutionId,
        TaskMessageRole role,
        TaskStatus status,
        String content,
        String summary,
        String detail,
        String recommendedAction,
        List<TaskArtifactResponse> artifacts,
        LocalDateTime createdAt) {

    public TaskMessageResponse {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static TaskMessageResponse of(TaskMessage message, List<TaskArtifactResponse> artifacts) {
        return new TaskMessageResponse(
                message.getId(),
                message.getTaskId(),
                message.getTaskExecutionId(),
                message.getRole(),
                TaskResponseStatusMapper.fromOptionalAgentStatus(message.getStatus()),
                message.getContent(),
                message.getSummary(),
                message.getDetail(),
                message.getRecommendedAction(),
                artifacts,
                message.getCreatedAt());
    }
}
