package back.domain.orchestrator.service;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.artifact.dto.StoredArtifactFile;
import back.domain.artifact.service.WorkspaceArtifactStorage;
import back.domain.execution.service.AgentExecutionResult;
import back.domain.execution.service.AgentExecutionResultParser;
import back.domain.execution.service.AgentExecutionStatus;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.entity.OrchestrationPlanStep;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.domain.orchestrator.repository.OrchestrationPlanStepRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring injects service collaborators managed by the application context.")
public class OrchestrationPlanRunnerImpl implements OrchestrationPlanRunner {

    private static final String FILE_STORAGE_WARNING_PREFIX = "파일 산출물 저장 경고: ";

    private final TransactionOperations transactionOperations;
    private final OrchestrationPlanRepository orchestrationPlanRepository;
    private final OrchestrationPlanStepRepository orchestrationPlanStepRepository;
    private final AgentRepository agentRepository;
    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;
    private final OpenClawGatewayClientFactory openClawGatewayClientFactory;
    private final AgentExecutionResultParser agentExecutionResultParser;
    private final WorkspaceArtifactStorage workspaceArtifactStorage;

    @Override
    public void run(Long workspaceId, Long planId) {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(planId);
        PlanExecutionContext planContext = preparePlan(workspaceId, planId);
        OpenClawGatewayConnectionContext gatewayContext = getGatewayContext(workspaceId, planId);
        OpenClawGatewayClient client = openClawGatewayClientFactory.create();
        Map<String, StepExecutionSummary> completedSteps = new LinkedHashMap<>();
        try {
            client.connect(gatewayContext);
            for (StepExecutionContext stepContext : resolveExecutionOrder(planContext.steps())) {
                StepExecutionSummary summary = runStep(client, planContext, stepContext, completedSteps);
                if (summary.failed()) {
                    markPlanFailed(workspaceId, planId);
                    return;
                }
                if (summary.canceled()) {
                    markPlanCanceled(workspaceId, planId);
                    return;
                }
                completedSteps.put(stepContext.stepKey(), summary);
            }
            markPlanCompleted(workspaceId, planId);
        } catch (RuntimeException exception) {
            markPlanFailed(workspaceId, planId);
            throw exception;
        } finally {
            client.close();
        }
    }

    private PlanExecutionContext preparePlan(Long workspaceId, Long planId) {
        return requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = orchestrationPlanRepository
                    .findByIdAndWorkspaceId(planId, workspaceId)
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[OrchestrationPlanRunnerImpl#preparePlan] plan not found. "
                                    + "workspaceId=" + workspaceId + ", planId=" + planId,
                            "Orchestration 계획을 찾을 수 없습니다."));
            plan.markRunning();
            orchestrationPlanRepository.save(plan);
            List<StepExecutionContext> steps = orchestrationPlanStepRepository
                    .findByPlanIdOrderBySequenceNoAscIdAsc(planId)
                    .stream()
                    .map(this::toStepExecutionContext)
                    .toList();
            return new PlanExecutionContext(
                    plan.getId(),
                    plan.getWorkspaceId(),
                    plan.getTitle(),
                    plan.getOrchestratorAgentId(),
                    steps);
        }));
    }

    private OpenClawGatewayConnectionContext getGatewayContext(Long workspaceId, Long planId) {
        try {
            return workspaceGatewayBindingService.getConnectionContext(workspaceId);
        } catch (RuntimeException exception) {
            markPlanFailed(workspaceId, planId);
            throw exception;
        }
    }

    private StepExecutionSummary runStep(
            OpenClawGatewayClient client,
            PlanExecutionContext planContext,
            StepExecutionContext stepContext,
            Map<String, StepExecutionSummary> completedSteps) {
        Agent agent;
        try {
            agent = resolveAgent(planContext.workspaceId(), stepContext);
            markStepRunning(stepContext.id(), agent.getId());
            OpenClawChatResult chatResult = client.sendChat(new OpenClawChatCommand(
                    agent.getOpenClawAgentId(),
                    resolveSessionKey(planContext, stepContext),
                    buildWorkerMessage(planContext, stepContext, completedSteps),
                    UUID.randomUUID().toString()));
            AgentExecutionResult result = agentExecutionResultParser.parse(chatResult.finalText());
            StepExecutionSummary summary = toStepExecutionSummary(stepContext, result, chatResult.finalText());
            saveStepResult(stepContext.id(), summary);
            return summary;
        } catch (OpenClawGatewayException exception) {
            return failStep(stepContext, exception.getClientMessage(), null);
        } catch (RuntimeException exception) {
            return failStep(stepContext, resolveFailureReason(exception), null);
        }
    }

    private Agent resolveAgent(Long workspaceId, StepExecutionContext stepContext) {
        if (stepContext.agentId() != null) {
            Agent agent = agentRepository.findByIdAndWorkspaceId(stepContext.agentId(), workspaceId)
                    .orElseThrow(() -> executionError("선택한 Worker Agent를 찾을 수 없습니다."));
            validateExecutableAgent(agent);
            return agent;
        }
        if (stepContext.agentName() != null) {
            Agent agent = agentRepository.findByWorkspaceIdAndName(workspaceId, stepContext.agentName())
                    .orElseThrow(() -> executionError("선택한 Worker Agent를 찾을 수 없습니다."));
            validateExecutableAgent(agent);
            return agent;
        }
        Agent agent = agentRepository
                .findFirstByWorkspaceIdAndCategoryAndStatusAndOpenClawAgentIdIsNotNullOrderByIdAsc(
                        workspaceId, stepContext.category(), AgentStatus.READY)
                .orElseThrow(() -> executionError("실행 가능한 Worker Agent가 없습니다."));
        validateExecutableAgent(agent);
        return agent;
    }

    private void validateExecutableAgent(Agent agent) {
        if (agent.getStatus() != AgentStatus.READY
                || agent.getOpenClawAgentId() == null
                || agent.getOpenClawAgentId().isBlank()) {
            throw executionError("Worker Agent가 READY 상태가 아닙니다.");
        }
    }

    private StepExecutionSummary toStepExecutionSummary(
            StepExecutionContext stepContext,
            AgentExecutionResult result,
            String finalText) {
        StoredFilesResult storedFiles = storeFiles(stepContext, result);
        String detail = appendStorageWarning(result.report().detail(), storedFiles.warningMessage());
        if (result.status() == AgentExecutionStatus.COMPLETED) {
            return StepExecutionSummary.completed(
                    result.report().status(),
                    result.report().summary(),
                    detail,
                    storedFiles.filePaths(),
                    result.risks(),
                    result.nextActions(),
                    finalText);
        }
        if (result.status() == AgentExecutionStatus.CANCELED) {
            return StepExecutionSummary.canceled(resolveFailureReason(result), finalText);
        }
        return StepExecutionSummary.failed(resolveFailureReason(result), finalText);
    }

    private StoredFilesResult storeFiles(StepExecutionContext stepContext, AgentExecutionResult result) {
        if (result.files().isEmpty()) {
            return new StoredFilesResult(List.of(), null);
        }
        try {
            List<String> filePaths = workspaceArtifactStorage
                    .storeFiles(stepContext.workspaceId(), result.files())
                    .stream()
                    .map(StoredArtifactFile::relativePath)
                    .toList();
            return new StoredFilesResult(filePaths, null);
        } catch (RuntimeException exception) {
            log.warn(
                    "Orchestration step file storage failed. workspaceId={}, planId={}, stepId={}",
                    stepContext.workspaceId(),
                    stepContext.planId(),
                    stepContext.id(),
                    exception);
            return new StoredFilesResult(List.of(), resolveFailureReason(exception));
        }
    }

    private StepExecutionSummary failStep(
            StepExecutionContext stepContext,
            String failureReason,
            String finalText) {
        StepExecutionSummary summary = StepExecutionSummary.failed(failureReason, finalText);
        saveStepResult(stepContext.id(), summary);
        return summary;
    }

    private void markStepRunning(Long stepId, Long agentId) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlanStep step = getStepOrThrow(stepId);
            step.markRunning(agentId);
            return orchestrationPlanStepRepository.save(step);
        }));
    }

    private void saveStepResult(Long stepId, StepExecutionSummary summary) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlanStep step = getStepOrThrow(stepId);
            if (summary.failed()) {
                step.markFailed(summary.failureReason(), summary.finalText());
            } else if (summary.canceled()) {
                step.markCanceled(summary.failureReason(), summary.finalText());
            } else {
                step.markCompleted(
                        summary.resultStatus(),
                        summary.summary(),
                        summary.detail(),
                        summary.filePaths(),
                        summary.risks(),
                        summary.nextActions(),
                        summary.finalText());
            }
            return orchestrationPlanStepRepository.save(step);
        }));
    }

    private void markPlanCompleted(Long workspaceId, Long planId) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = getPlanOrThrow(workspaceId, planId);
            plan.markCompleted();
            return orchestrationPlanRepository.save(plan);
        }));
    }

    private void markPlanFailed(Long workspaceId, Long planId) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = getPlanOrThrow(workspaceId, planId);
            plan.markFailed();
            return orchestrationPlanRepository.save(plan);
        }));
    }

    private void markPlanCanceled(Long workspaceId, Long planId) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = getPlanOrThrow(workspaceId, planId);
            plan.markCanceled();
            return orchestrationPlanRepository.save(plan);
        }));
    }

    private OrchestrationPlan getPlanOrThrow(Long workspaceId, Long planId) {
        return orchestrationPlanRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> executionError("Orchestration 계획을 찾을 수 없습니다."));
    }

    private OrchestrationPlanStep getStepOrThrow(Long stepId) {
        return orchestrationPlanStepRepository.findById(stepId)
                .orElseThrow(() -> executionError("Orchestration step을 찾을 수 없습니다."));
    }

    private List<StepExecutionContext> resolveExecutionOrder(List<StepExecutionContext> steps) {
        Map<String, StepExecutionContext> stepsByKey = new HashMap<>();
        steps.forEach(step -> stepsByKey.put(step.stepKey(), step));
        List<StepExecutionContext> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        steps.stream()
                .sorted(Comparator.comparingInt(StepExecutionContext::sequenceNo))
                .forEach(step -> visitStep(step, stepsByKey, visiting, visited, ordered));
        return ordered;
    }

    private void visitStep(
            StepExecutionContext step,
            Map<String, StepExecutionContext> stepsByKey,
            Set<String> visiting,
            Set<String> visited,
            List<StepExecutionContext> ordered) {
        if (visited.contains(step.stepKey())) {
            return;
        }
        if (!visiting.add(step.stepKey())) {
            throw executionError("dependsOn 순환 참조가 있습니다.");
        }
        for (String dependencyKey : step.dependsOn()) {
            StepExecutionContext dependency = stepsByKey.get(dependencyKey);
            if (dependency == null) {
                throw executionError("dependsOn 대상 step을 찾을 수 없습니다.");
            }
            visitStep(dependency, stepsByKey, visiting, visited, ordered);
        }
        visiting.remove(step.stepKey());
        visited.add(step.stepKey());
        ordered.add(step);
    }

    private StepExecutionContext toStepExecutionContext(OrchestrationPlanStep step) {
        return new StepExecutionContext(
                step.getId(),
                step.getPlan().getId(),
                step.getPlan().getWorkspaceId(),
                step.getSequenceNo(),
                step.getStepKey(),
                step.getAgentId(),
                step.getAgentName(),
                step.getCategory(),
                step.getTitle(),
                step.getPrompt(),
                step.getDependsOnStepKeys());
    }

    private String buildWorkerMessage(
            PlanExecutionContext planContext,
            StepExecutionContext stepContext,
            Map<String, StepExecutionSummary> completedSteps) {
        return String.join(
                System.lineSeparator(),
                "Orchestration Worker Context",
                "- workspaceId: " + planContext.workspaceId(),
                "- orchestrationPlanId: " + planContext.planId(),
                "- planTitle: " + planContext.title(),
                "- stepId: " + stepContext.id(),
                "- stepKey: " + stepContext.stepKey(),
                "- stepTitle: " + stepContext.title(),
                "- projectRoot: " + workspaceArtifactStorage.resolveProjectRoot(planContext.workspaceId()),
                "",
                "Completed dependency results",
                formatCompletedSteps(completedSteps),
                "",
                "Worker instruction",
                stepContext.prompt(),
                "",
                "Final report must be a JSON object.",
                "Required fields: status, summary, detail, recommendedAction.",
                "Allowed status values: COMPLETED, FAILED, CANCELED.",
                "Optional fields: files [{ path, content }], risks [string], nextActions [string].",
                "Do not expose GitHub PAT, Slack token, Gateway token, or any credential value.");
    }

    private String formatCompletedSteps(Map<String, StepExecutionSummary> completedSteps) {
        if (completedSteps.isEmpty()) {
            return "- none";
        }
        return completedSteps.entrySet().stream()
                .map(entry -> "- "
                        + entry.getKey()
                        + ": "
                        + entry.getValue().summary()
                        + formatFilePaths(entry.getValue().filePaths()))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private String formatFilePaths(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return "";
        }
        return " files=" + String.join(", ", filePaths);
    }

    private String resolveSessionKey(PlanExecutionContext planContext, StepExecutionContext stepContext) {
        return "workspace-"
                + planContext.workspaceId()
                + "-orchestration-"
                + planContext.planId()
                + "-step-"
                + stepContext.id();
    }

    private String appendStorageWarning(String detail, String warningMessage) {
        if (warningMessage == null || warningMessage.isBlank()) {
            return detail;
        }
        String warning = FILE_STORAGE_WARNING_PREFIX + warningMessage;
        if (detail == null || detail.isBlank()) {
            return warning;
        }
        return detail + System.lineSeparator() + System.lineSeparator() + warning;
    }

    private String resolveFailureReason(AgentExecutionResult result) {
        if (result.report().detail() != null && !result.report().detail().isBlank()) {
            return result.report().detail();
        }
        return result.report().summary();
    }

    private String resolveFailureReason(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException) {
            return serviceException.getClientMessage();
        }
        return "Worker Agent 실행 중 오류가 발생했습니다.";
    }

    private ServiceException executionError(String clientMessage) {
        return new ServiceException(
                CommonErrorCode.BAD_REQUEST_STATE,
                "[OrchestrationPlanRunnerImpl] " + clientMessage,
                clientMessage);
    }

    private <T> T requireTransactionResult(T result) {
        return Objects.requireNonNull(result, "transaction result must not be null");
    }

    private record PlanExecutionContext(
            Long planId,
            Long workspaceId,
            String title,
            Long orchestratorAgentId,
            List<StepExecutionContext> steps) {}

    private record StepExecutionContext(
            Long id,
            Long planId,
            Long workspaceId,
            int sequenceNo,
            String stepKey,
            Long agentId,
            String agentName,
            AgentCategory category,
            String title,
            String prompt,
            List<String> dependsOn) {

        private StepExecutionContext {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }

    private record StoredFilesResult(List<String> filePaths, String warningMessage) {

        private StoredFilesResult {
            filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
        }
    }

    private record StepExecutionSummary(
            boolean failed,
            boolean canceled,
            String resultStatus,
            String summary,
            String detail,
            List<String> filePaths,
            List<String> risks,
            List<String> nextActions,
            String finalText,
            String failureReason) {

        private StepExecutionSummary {
            filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
            risks = risks == null ? List.of() : List.copyOf(risks);
            nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        }

        private static StepExecutionSummary completed(
                String resultStatus,
                String summary,
                String detail,
                List<String> filePaths,
                List<String> risks,
                List<String> nextActions,
                String finalText) {
            return new StepExecutionSummary(
                    false,
                    false,
                    resultStatus,
                    summary,
                    detail,
                    filePaths,
                    risks,
                    nextActions,
                    finalText,
                    null);
        }

        private static StepExecutionSummary failed(String failureReason, String finalText) {
            return new StepExecutionSummary(
                    true,
                    false,
                    AgentExecutionStatus.FAILED.reportStatus(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    finalText,
                    failureReason);
        }

        private static StepExecutionSummary canceled(String cancelReason, String finalText) {
            return new StepExecutionSummary(
                    false,
                    true,
                    AgentExecutionStatus.CANCELED.reportStatus(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    finalText,
                    cancelReason);
        }
    }
}
