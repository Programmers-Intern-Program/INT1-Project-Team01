package back.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.chat.entity.ChatSession;
import back.domain.chat.entity.ChatSessionSource;
import back.domain.chat.entity.ChatSessionStatus;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<ChatSession> findByWorkspaceIdAndSourceAndSourceRef(
            Long workspaceId, ChatSessionSource source, String sourceRef);

    List<ChatSession> findByWorkspaceIdAndAgentIdAndStatusOrderByLastMessageAtDescIdDesc(
            Long workspaceId, Long agentId, ChatSessionStatus status);
}
