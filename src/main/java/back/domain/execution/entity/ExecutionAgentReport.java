package back.domain.execution.entity;

import back.domain.execution.dto.request.AgentReportSaveRequest;
import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "JPA entity constructors validate domain invariants and this entity has no finalizer.")
@Getter
@Entity(name = "ExecutionAgentReport")
@Table(
        name = "execution_agent_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_execution_agent_report_task_execution", columnNames = "task_execution_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionAgentReport extends BaseEntity {

    @Column(name = "task_execution_id", nullable = false)
    private Long taskExecutionId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    private ExecutionAgentReport(Long taskExecutionId, AgentReportSaveRequest request) {
        this.taskExecutionId = requireId(taskExecutionId, "taskExecutionId");
        this.status = limitLength(request.status(), 30);
        this.summary = limitLength(request.summary(), 500);
        this.detail = request.detail();
        this.recommendedAction = request.recommendedAction();
    }

    public static ExecutionAgentReport create(Long taskExecutionId, AgentReportSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return new ExecutionAgentReport(taskExecutionId, request);
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static String limitLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
