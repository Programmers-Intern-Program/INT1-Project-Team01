package back.domain.chat.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageResponse;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.service.ChatService;
import back.domain.slack.event.SlackReplyRequestedEvent;
import back.domain.slack.port.SlackConversationPort;
import back.global.exception.ServiceException;
import back.global.util.HashUtils;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlackConversationAdapter implements SlackConversationPort {

    private static final String FAILURE_MESSAGE = "Agent 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";
    private static final int FAILURE_REPLY_KEY_HASH_LENGTH = 16;

    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String sendMessage(Long workspaceId, String sourceRef, String agentName, String text) {
        try {
            ChatMessageSendResponse response =
                    chatService.sendSlackMessage(
                            workspaceId,
                            new SlackChatMessageSendCommand(sourceRef, text, agentName));
            publishImmediateReplyIfNeeded(sourceRef, response);
            return response.finalText();
        } catch (RuntimeException exception) {
            publishFailureReply(sourceRef, agentName, text, exception);
            throw exception;
        }
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

    private void publishFailureReply(String sourceRef, String agentName, String text, RuntimeException exception) {
        try {
            eventPublisher.publishEvent(new SlackReplyRequestedEvent(
                    sourceRef,
                    resolveFailureMessage(exception),
                    resolveFailureReplyKey(sourceRef, agentName, text)));
        } catch (RuntimeException replyException) {
            exception.addSuppressed(replyException);
        }
    }

    private String resolveFailureMessage(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException) {
            String clientMessage = serviceException.getClientMessage();
            if (clientMessage != null && !clientMessage.isBlank()) {
                return clientMessage;
            }
        }
        return FAILURE_MESSAGE;
    }

    private String resolveFailureReplyKey(String sourceRef, String agentName, String text) {
        String keySource = normalize(sourceRef) + "|" + normalize(agentName) + "|" + normalize(text);
        return "slack-chat-failure-" + HashUtils.sha256Hex(keySource).substring(0, FAILURE_REPLY_KEY_HASH_LENGTH);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

}
