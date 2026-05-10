package back.domain.orchestrator.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import back.global.jpa.entity.BaseEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
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
        name = "orchestration_plans",
        indexes = {
            @Index(name = "idx_orchestration_plans_workspace_session", columnList = "workspace_id, chat_session_id"),
            @Index(name = "idx_orchestration_plans_workspace_status", columnList = "workspace_id, status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrchestrationPlan extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "chat_session_id", nullable = false)
    private Long chatSessionId;

    @Column(name = "orchestrator_agent_id", nullable = false)
    private Long orchestratorAgentId;

    @Column(name = "user_message_id", nullable = false)
    private Long userMessageId;

    @Column(name = "assistant_message_id")
    private Long assistantMessageId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrchestrationPlanStatus status;

    @Column(name = "raw_response", nullable = false, columnDefinition = "TEXT")
    private String rawResponse;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrchestrationPlanStep> steps = new ArrayList<>();

    private OrchestrationPlan(
            Long workspaceId,
            Long chatSessionId,
            Long orchestratorAgentId,
            Long userMessageId,
            String title,
            String rawResponse) {
        this.workspaceId = requirePositive(workspaceId, "workspaceId");
        this.chatSessionId = requirePositive(chatSessionId, "chatSessionId");
        this.orchestratorAgentId = requirePositive(orchestratorAgentId, "orchestratorAgentId");
        this.userMessageId = requirePositive(userMessageId, "userMessageId");
        this.title = requireNotBlank(title, "title");
        this.status = OrchestrationPlanStatus.PLANNED;
        this.rawResponse = requireNotBlank(rawResponse, "rawResponse");
    }

    public static OrchestrationPlan create(
            Long workspaceId,
            Long chatSessionId,
            Long orchestratorAgentId,
            Long userMessageId,
            String title,
            String rawResponse) {
        return new OrchestrationPlan(
                workspaceId,
                chatSessionId,
                orchestratorAgentId,
                userMessageId,
                title,
                rawResponse);
    }

    public void addStep(OrchestrationPlanStep step) {
        if (step == null) {
            throw new IllegalArgumentException("step must not be null");
        }
        steps.add(step);
    }

    public void linkAssistantMessage(Long assistantMessageId) {
        this.assistantMessageId = requirePositive(assistantMessageId, "assistantMessageId");
    }

    public void markRunning() {
        this.status = OrchestrationPlanStatus.RUNNING;
    }

    public void markCompleted() {
        this.status = OrchestrationPlanStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = OrchestrationPlanStatus.FAILED;
    }

    public List<OrchestrationPlanStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
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
}
