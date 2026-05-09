package back.domain.chat.dto.response;

import java.util.List;

public record ChatMessagesResponse(
        Long chatSessionId,
        List<ChatMessageResponse> messages,
        Long nextCursor,
        boolean hasMore) {

    public ChatMessagesResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
