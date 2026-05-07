package back.domain.execution.entity;

import back.domain.execution.dto.request.TaskArtifactSaveRequest;
import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "JPA entity constructors validate domain invariants and this entity has no finalizer.")
@Getter
@Entity(name = "ExecutionTaskArtifact")
@Table(
        name = "execution_task_artifacts",
        indexes = @Index(name = "idx_execution_task_artifacts_execution", columnList = "task_execution_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionTaskArtifact extends BaseEntity {

    @Column(name = "task_execution_id", nullable = false)
    private Long taskExecutionId;

    @Column(nullable = false, length = 80)
    private String artifactType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String url;

    private ExecutionTaskArtifact(Long taskExecutionId, TaskArtifactSaveRequest request) {
        this.taskExecutionId = requireId(taskExecutionId, "taskExecutionId");
        this.artifactType = limitLength(request.artifactType(), 80);
        this.name = limitLength(request.name(), 200);
        this.url = limitLength(request.url(), 1000);
    }

    public static ExecutionTaskArtifact create(Long taskExecutionId, TaskArtifactSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return new ExecutionTaskArtifact(taskExecutionId, request);
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
