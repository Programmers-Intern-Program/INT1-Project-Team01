package back.domain.task.domain;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskExecution extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskExecutionStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    private TaskExecution(Long taskId) {
        this.taskId = taskId;
        this.status = TaskExecutionStatus.PENDING;
    }

    public static TaskExecution create(Long taskId) {
        return new TaskExecution(taskId);
    }

    public void start() {
        this.status = TaskExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void success() {
        this.status = TaskExecutionStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String failureReason) {
        this.status = TaskExecutionStatus.FAILED;
        this.failureReason = failureReason;
        this.finishedAt = LocalDateTime.now();
    }
}