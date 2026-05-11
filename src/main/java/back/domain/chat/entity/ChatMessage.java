package back.domain.chat.entity;

import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "JPA entity constructors validate domain invariants and this entity has no finalizer.")
@Getter
@Entity
@Table(
        name = "chat_messages",
        indexes = {
            @Index(
                    name = "idx_chat_messages_workspace_session",
                    columnList = "workspace_id, chat_session_id, created_at"),
            @Index(name = "idx_chat_messages_workspace_task", columnList = "workspace_id, task_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "chat_session_id", nullable = false)
    private Long chatSessionId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_execution_id")
    private Long taskExecutionId;

    @Column(name = "orchestration_plan_id")
    private Long orchestrationPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private ChatMessage(
            Long workspaceId,
            Long chatSessionId,
            Long taskId,
            Long taskExecutionId,
            Long orchestrationPlanId,
            ChatMessageRole role,
            String content) {
        this.workspaceId = requireId(workspaceId, "workspaceId");
        this.chatSessionId = requireId(chatSessionId, "chatSessionId");
        this.taskId = requireOptionalId(taskId, "taskId");
        this.taskExecutionId = requireOptionalId(taskExecutionId, "taskExecutionId");
        this.orchestrationPlanId = requireOptionalId(orchestrationPlanId, "orchestrationPlanId");
        this.role = requireRole(role);
        this.content = requireNotBlank(content, "content");
    }

    public static ChatMessage user(Long workspaceId, Long chatSessionId, String content) {
        return new ChatMessage(workspaceId, chatSessionId, null, null, null, ChatMessageRole.USER, content);
    }

    public static ChatMessage assistant(Long workspaceId, Long chatSessionId, String content) {
        return new ChatMessage(workspaceId, chatSessionId, null, null, null, ChatMessageRole.ASSISTANT, content);
    }

    public static ChatMessage assistantForTask(
            Long workspaceId, Long chatSessionId, Long taskId, Long taskExecutionId, String content) {
        return new ChatMessage(
                workspaceId,
                chatSessionId,
                requireId(taskId, "taskId"),
                requireOptionalId(taskExecutionId, "taskExecutionId"),
                null,
                ChatMessageRole.ASSISTANT,
                content);
    }

    public static ChatMessage assistantForOrchestration(
            Long workspaceId, Long chatSessionId, Long orchestrationPlanId, String content) {
        return new ChatMessage(
                workspaceId,
                chatSessionId,
                null,
                null,
                requireId(orchestrationPlanId, "orchestrationPlanId"),
                ChatMessageRole.ASSISTANT,
                content);
    }

    public static ChatMessage system(Long workspaceId, Long chatSessionId, String content) {
        return new ChatMessage(workspaceId, chatSessionId, null, null, null, ChatMessageRole.SYSTEM, content);
    }

    public void linkTask(Long taskId, Long taskExecutionId) {
        Long nextTaskId = requireId(taskId, "taskId");
        Long nextTaskExecutionId = requireOptionalId(taskExecutionId, "taskExecutionId");
        if (this.taskId != null && !this.taskId.equals(nextTaskId)) {
            throw new IllegalStateException("message is already linked to another task");
        }
        if (this.taskExecutionId != null && !this.taskExecutionId.equals(nextTaskExecutionId)) {
            throw new IllegalStateException("message is already linked to another task execution");
        }
        this.taskId = nextTaskId;
        this.taskExecutionId = nextTaskExecutionId;
    }

    public void linkOrchestrationPlan(Long orchestrationPlanId) {
        Long nextOrchestrationPlanId = requireId(orchestrationPlanId, "orchestrationPlanId");
        if (this.orchestrationPlanId != null && !this.orchestrationPlanId.equals(nextOrchestrationPlanId)) {
            throw new IllegalStateException("message is already linked to another orchestration plan");
        }
        this.orchestrationPlanId = nextOrchestrationPlanId;
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static Long requireOptionalId(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        return requireId(value, fieldName);
    }

    private static ChatMessageRole requireRole(ChatMessageRole value) {
        if (value == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return value;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
