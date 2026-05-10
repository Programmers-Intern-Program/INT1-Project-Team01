package back.domain.task.entity;

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
        name = "task_messages",
        indexes = @Index(name = "idx_task_messages_workspace_task", columnList = "workspace_id, task_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskMessage extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_execution_id")
    private Long taskExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskMessageRole role;

    @Column(length = 30)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    private TaskMessage(
            Long workspaceId,
            Long taskId,
            Long taskExecutionId,
            TaskMessageRole role,
            String status,
            String content,
            String summary,
            String detail,
            String recommendedAction) {
        this.workspaceId = requireId(workspaceId, "workspaceId");
        this.taskId = requireId(taskId, "taskId");
        this.taskExecutionId = requireOptionalId(taskExecutionId, "taskExecutionId");
        this.role = requireRole(role);
        this.status = limitLength(normalizeOptional(status), 30);
        this.content = requireNotBlank(content, "content");
        this.summary = limitLength(normalizeOptional(summary), 500);
        this.detail = normalizeOptional(detail);
        this.recommendedAction = normalizeOptional(recommendedAction);
    }

    public static TaskMessage assistantResponse(
            Long workspaceId,
            Long taskId,
            Long taskExecutionId,
            String status,
            String content,
            String summary,
            String detail,
            String recommendedAction) {
        return new TaskMessage(
                workspaceId,
                taskId,
                taskExecutionId,
                TaskMessageRole.ASSISTANT,
                status,
                content,
                summary,
                detail,
                recommendedAction);
    }

    public static TaskMessage userRequest(Long workspaceId, Long taskId, String content) {
        return new TaskMessage(
                workspaceId,
                taskId,
                null,
                TaskMessageRole.USER,
                null,
                content,
                null,
                null,
                null);
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

    private static TaskMessageRole requireRole(TaskMessageRole value) {
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

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
