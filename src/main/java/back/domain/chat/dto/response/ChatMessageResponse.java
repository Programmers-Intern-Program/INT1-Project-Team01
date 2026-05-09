package back.domain.chat.dto.response;

import java.time.LocalDateTime;

import back.domain.chat.entity.ChatMessage;
import back.domain.chat.entity.ChatMessageRole;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageResponse(
        Long messageId,
        Long chatSessionId,
        Long taskId,
        Long taskExecutionId,
        ChatMessageRole role,
        String content,
        LocalDateTime createdAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatSessionId(),
                message.getTaskId(),
                message.getTaskExecutionId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}
