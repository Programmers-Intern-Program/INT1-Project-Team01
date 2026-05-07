package back.domain.task.entity;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task_execution_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskExecutionLog extends BaseEntity {

    @Column(nullable = false)
    private Long executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    private TaskExecutionLog(Long executionId, LogLevel level, String message) {
        this.executionId = executionId;
        this.level = level;
        this.message = message;
    }

    public static TaskExecutionLog create(Long executionId, LogLevel level, String message) {
        return new TaskExecutionLog(executionId, level, message);
    }
}