package back.domain.execution.entity;

import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "JPA entity constructors validate domain invariants and this entity has no finalizer.")
@Getter
@Entity
@Table(name = "task_executions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskExecution extends BaseEntity {

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Long agentId;

    @Column(nullable = false, length = 160)
    private String openClawAgentId;

    private Long repositoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskExecutionStatus status;

    @Column(length = 512)
    private String workdirPath;

    @Column(length = 220)
    private String openClawSessionKey;

    @Column(length = 220)
    private String branchName;

    @Column(length = 1000)
    private String failureReason;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private TaskExecution(
            Long workspaceId, Long taskId, Long agentId, String openClawAgentId, Long repositoryId, String branchName) {
        this.workspaceId = requireId(workspaceId, "workspaceId");
        this.taskId = requireId(taskId, "taskId");
        this.agentId = requireId(agentId, "agentId");
        this.openClawAgentId = requireNotBlank(openClawAgentId, "openClawAgentId");
        this.repositoryId = requireOptionalId(repositoryId, "repositoryId");
        this.branchName = normalizeOptional(branchName);
        this.status = TaskExecutionStatus.QUEUED;
    }

    public static TaskExecution queued(
            Long workspaceId,
            Long taskId,
            Long agentId,
            String openClawAgentId,
            Long repositoryId,
            String branchName) {
        return new TaskExecution(workspaceId, taskId, agentId, openClawAgentId, repositoryId, branchName);
    }

    public static TaskExecution create(
            Long workspaceId,
            Long taskId,
            Long agentId,
            String openClawAgentId,
            Long repositoryId,
            String branchName
    ) {
        return queued(
                workspaceId,
                taskId,
                agentId,
                openClawAgentId,
                repositoryId,
                branchName
        );
    }


    public void assignRuntimeContext(String workdirPath, String openClawSessionKey) {
        this.workdirPath = requireNotBlank(workdirPath, "workdirPath");
        this.openClawSessionKey = requireNotBlank(openClawSessionKey, "openClawSessionKey");
    }

    public void markRunning() {
        this.status = TaskExecutionStatus.RUNNING;
        this.failureReason = null;
        this.startedAt = LocalDateTime.now();
    }

    public void markSucceeded() {
        this.status = TaskExecutionStatus.SUCCEEDED;
        this.failureReason = null;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String failureReason) {
        this.status = TaskExecutionStatus.FAILED;
        this.failureReason = normalizeError(failureReason);
        this.finishedAt = LocalDateTime.now();
    }

    public void start() {
        markRunning();
    }

    public void success() {
        markSucceeded();
    }

    public void fail(String failureReason) {
        markFailed(failureReason);
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

    private static String normalizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 1000) {
            return trimmed;
        }
        return trimmed.substring(0, 1000);
    }




}
