package back.domain.task.domain;

import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentReport extends BaseEntity {

    @Column(nullable = false)
    private Long taskId;

    private Long executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    private AgentReport(
            Long taskId,
            Long executionId,
            TaskStatus status,
            String summary,
            String detail,
            String recommendedAction
    ) {
        this.taskId = taskId;
        this.executionId = executionId;
        this.status = status;
        this.summary = summary;
        this.detail = detail;
        this.recommendedAction = recommendedAction;
    }

    public static AgentReport create(
            Long taskId,
            Long executionId,
            TaskStatus status,
            String summary,
            String detail,
            String recommendedAction
    ) {
        return new AgentReport(taskId, executionId, status, summary, detail, recommendedAction);
    }
}