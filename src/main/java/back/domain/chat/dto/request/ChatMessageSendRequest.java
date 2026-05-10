package back.domain.chat.dto.request;

import back.domain.task.dto.request.TaskRunRequest;
import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank(message = "채팅 메시지는 필수입니다.")
        @Size(max = 2000, message = "채팅 메시지는 2000자 이하여야 합니다.")
        String message,

        @NotNull(message = "agentId는 필수입니다.")
        @Positive(message = "agentId는 양수여야 합니다.")
        Long agentId,

        @Positive(message = "repositoryId는 양수여야 합니다.")
        Long repositoryId,

        TaskType taskType,

        TaskPriority priority,

        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        Boolean createPr,

        @Positive(message = "chatSessionId는 양수여야 합니다.")
        Long chatSessionId) {

    private static final int TITLE_PREVIEW_LENGTH = 60;

    public ChatMessageSendRequest(
            String message,
            Long agentId,
            Long repositoryId,
            TaskType taskType,
            TaskPriority priority,
            String title,
            Boolean createPr) {
        this(message, agentId, repositoryId, taskType, priority, title, createPr, null);
    }

    public TaskRunRequest toTaskRunRequest() {
        String normalizedMessage = message.trim();
        return new TaskRunRequest(
                resolveTitle(normalizedMessage),
                normalizedMessage,
                taskType == null ? TaskType.OTHER : taskType,
                priority == null ? TaskPriority.MEDIUM : priority,
                agentId,
                repositoryId,
                SourceType.DASHBOARD,
                "chat",
                normalizedMessage,
                createPr);
    }

    public boolean shouldCreatePr() {
        return Boolean.TRUE.equals(createPr);
    }

    private String resolveTitle(String normalizedMessage) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (normalizedMessage.length() <= TITLE_PREVIEW_LENGTH) {
            return normalizedMessage;
        }
        return normalizedMessage.substring(0, TITLE_PREVIEW_LENGTH);
    }
}
