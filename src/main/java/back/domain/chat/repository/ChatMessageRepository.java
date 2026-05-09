package back.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByWorkspaceIdAndChatSessionIdOrderByCreatedAtAscIdAsc(Long workspaceId, Long chatSessionId);

    List<ChatMessage> findByWorkspaceIdAndChatSessionIdAndTaskIdOrderByCreatedAtAscIdAsc(
            Long workspaceId, Long chatSessionId, Long taskId);
}
