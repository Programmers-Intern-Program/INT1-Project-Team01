package back.domain.chat.controller;

import back.domain.chat.dto.request.ChatMessageSendRequest;
import back.domain.chat.dto.response.ChatMessageSendResponse;
import back.domain.chat.service.ChatService;
import back.domain.task.dto.response.TaskMessageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/chat")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageSendResponse> sendMessage(
            @PathVariable Long workspaceId, @Valid @RequestBody ChatMessageSendRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(workspaceId, request));
    }

    @GetMapping("/tasks/{taskId}/messages")
    public ResponseEntity<List<TaskMessageResponse>> getMessages(
            @PathVariable Long workspaceId, @PathVariable Long taskId) {
        return ResponseEntity.ok(chatService.getMessages(workspaceId, taskId));
    }
}
