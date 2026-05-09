package back.domain.chat.adapter;

import org.springframework.stereotype.Component;

import back.domain.chat.dto.request.SlackChatMessageSendCommand;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.service.ChatService;
import back.domain.slack.port.SlackConversationPort;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlackConversationAdapter implements SlackConversationPort {

    private final ChatService chatService;

    @Override
    public String sendMessage(Long workspaceId, String sourceRef, String agentName, String text) {
        ChatMessageSendResponse response =
                chatService.sendSlackMessage(workspaceId, new SlackChatMessageSendCommand(sourceRef, text, agentName));
        return response.finalText();
    }
}
