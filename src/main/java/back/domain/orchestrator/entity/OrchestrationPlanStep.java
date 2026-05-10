package back.domain.orchestrator.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import back.domain.agent.entity.AgentCategory;
import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@SuppressFBWarnings(
        value = {"CT_CONSTRUCTOR_THROW", "EI_EXPOSE_REP"},
        justification = "JPA entity constructors validate domain invariants; plan reference is managed by JPA.")
@Getter
@Entity
@Table(
        name = "orchestration_plan_steps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orchestration_plan_steps_plan_step_key",
                columnNames = {"plan_id", "step_key"}),
        indexes = {
            @Index(name = "idx_orchestration_plan_steps_plan", columnList = "plan_id"),
            @Index(name = "idx_orchestration_plan_steps_agent", columnList = "agent_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrchestrationPlanStep extends BaseEntity {

    private static final String DEPENDS_ON_DELIMITER = ",";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private OrchestrationPlan plan;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "step_key", nullable = false, length = 80)
    private String stepKey;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "agent_name", length = 120)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AgentCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "depends_on_step_keys", columnDefinition = "TEXT")
    private String dependsOnStepKeys;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrchestrationPlanStepStatus status;

    @Column(name = "resolved_agent_id")
    private Long resolvedAgentId;

    @Column(name = "result_status", length = 30)
    private String resultStatus;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "result_detail", columnDefinition = "TEXT")
    private String resultDetail;

    @Column(name = "result_file_paths", columnDefinition = "TEXT")
    private String resultFilePaths;

    @Column(name = "result_risks", columnDefinition = "TEXT")
    private String resultRisks;

    @Column(name = "result_next_actions", columnDefinition = "TEXT")
    private String resultNextActions;

    @Column(name = "final_text", columnDefinition = "TEXT")
    private String finalText;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private OrchestrationPlanStep(
            OrchestrationPlan plan,
            int sequenceNo,
            String stepKey,
            Long agentId,
            String agentName,
            AgentCategory category,
            String title,
            String prompt,
            List<String> dependsOn) {
        this.plan = requirePlan(plan);
        this.sequenceNo = requireSequenceNo(sequenceNo);
        this.stepKey = requireNotBlank(stepKey, "stepKey");
        this.agentId = requireOptionalPositive(agentId, "agentId");
        this.agentName = normalizeOptional(agentName);
        this.category = category;
        this.title = requireNotBlank(title, "title");
        this.prompt = requireNotBlank(prompt, "prompt");
        this.dependsOnStepKeys = serializeDependsOn(dependsOn);
        this.status = OrchestrationPlanStepStatus.PENDING;
    }

    public static OrchestrationPlanStep create(
            OrchestrationPlan plan,
            int sequenceNo,
            String stepKey,
            Long agentId,
            String agentName,
            AgentCategory category,
            String title,
            String prompt,
            List<String> dependsOn) {
        return new OrchestrationPlanStep(
                plan,
                sequenceNo,
                stepKey,
                agentId,
                agentName,
                category,
                title,
                prompt,
                dependsOn);
    }

    public List<String> getDependsOnStepKeys() {
        if (dependsOnStepKeys == null || dependsOnStepKeys.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dependsOnStepKeys.split(DEPENDS_ON_DELIMITER))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    public OrchestrationPlanStepStatus getStatus() {
        if (status == null) {
            return OrchestrationPlanStepStatus.PENDING;
        }
        return status;
    }

    public void markRunning(Long resolvedAgentId) {
        this.status = OrchestrationPlanStepStatus.RUNNING;
        this.resolvedAgentId = requireOptionalPositive(resolvedAgentId, "resolvedAgentId");
        this.failureReason = null;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted(
            String resultStatus,
            String resultSummary,
            String resultDetail,
            List<String> filePaths,
            List<String> risks,
            List<String> nextActions,
            String finalText) {
        this.status = OrchestrationPlanStepStatus.COMPLETED;
        this.resultStatus = normalizeOptional(resultStatus);
        this.resultSummary = limitLength(normalizeOptional(resultSummary), 500);
        this.resultDetail = normalizeOptional(resultDetail);
        this.resultFilePaths = serializeLines(filePaths);
        this.resultRisks = serializeLines(risks);
        this.resultNextActions = serializeLines(nextActions);
        this.finalText = normalizeOptional(finalText);
        this.failureReason = null;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String failureReason, String finalText) {
        this.status = OrchestrationPlanStepStatus.FAILED;
        this.failureReason = limitLength(normalizeOptional(failureReason), 1000);
        this.finalText = normalizeOptional(finalText);
        this.finishedAt = LocalDateTime.now();
    }

    public void markCanceled(String cancelReason, String finalText) {
        this.status = OrchestrationPlanStepStatus.CANCELED;
        this.failureReason = limitLength(normalizeOptional(cancelReason), 1000);
        this.finalText = normalizeOptional(finalText);
        this.finishedAt = LocalDateTime.now();
    }

    private static OrchestrationPlan requirePlan(OrchestrationPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        return plan;
    }

    private static int requireSequenceNo(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("sequenceNo must be positive");
        }
        return value;
    }

    private static Long requireOptionalPositive(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
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

    private static String serializeDependsOn(List<String> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return null;
        }
        return dependsOn.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(DEPENDS_ON_DELIMITER));
    }

    private static String serializeLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("\n"));
        return joined.isBlank() ? null : joined;
    }

    private static String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
