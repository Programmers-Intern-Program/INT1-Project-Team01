package back.domain.chat.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.service.ChatService;
import back.domain.slack.port.SlackConversationPort;
import back.domain.slack.event.SlackReplyRequestedEvent;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlackConversationAdapter implements SlackConversationPort {

    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String sendMessage(Long workspaceId, String sourceRef, String agentName, String text) {
        ChatMessageSendResponse response =
                chatService.sendSlackMessage(workspaceId, new SlackChatMessageSendCommand(sourceRef, text, agentName));
        publishImmediateReplyIfNeeded(sourceRef, response);
        return response.finalText();
    }

    private void publishImmediateReplyIfNeeded(String sourceRef, ChatMessageSendResponse response) {
        if (response.taskId() != null || response.finalText() == null || response.finalText().isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new SlackReplyRequestedEvent(
                sourceRef,
                response.finalText(),
                resolveReplyKey(response)));
    }

    private String resolveReplyKey(ChatMessageSendResponse response) {
        return response.messages().stream()
                .filter(message -> response.finalText().equals(message.content()))
                .map(ChatMessageResponse::messageId)
                .filter(messageId -> messageId != null)
                .reduce((previous, current) -> current)
                .map(messageId -> "slack-chat-message-" + messageId)
                .orElse(null);
    }
}
