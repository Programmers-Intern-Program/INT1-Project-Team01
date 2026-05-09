package back.domain.chat.service;

import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.task.dto.response.TaskMessageResponse;
import java.util.List;

public interface ChatService {

    ChatMessageSendResponse sendMessage(Long workspaceId, ChatMessageSendRequest request);

    ChatMessageSendResponse sendSlackMessage(Long workspaceId, SlackChatMessageSendCommand command);

    List<ChatMessageResponse> getSessionMessages(Long workspaceId, Long chatSessionId);

    List<TaskMessageResponse> getMessages(Long workspaceId, Long taskId);
}
