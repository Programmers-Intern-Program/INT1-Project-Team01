package back.domain.orchestrator.service;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentCategory;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.artifact.dto.StoredArtifactFile;
import back.domain.artifact.service.WorkspaceArtifactStorage;
import back.domain.chat.entity.ChatMessage;
import back.domain.chat.entity.ChatSession;
import back.domain.chat.entity.ChatSessionSource;
import back.domain.chat.repository.ChatMessageRepository;
import back.domain.chat.repository.ChatSessionRepository;
import back.domain.execution.service.AgentExecutionResult;
import back.domain.execution.service.AgentExecutionResultParser;
import back.domain.execution.service.AgentExecutionStatus;
import back.domain.gateway.client.OpenClawChatCommand;
import back.domain.gateway.client.OpenClawChatResult;
import back.domain.gateway.client.OpenClawGatewayClient;
import back.domain.gateway.client.OpenClawGatewayClientFactory;
import back.domain.gateway.client.OpenClawGatewayConnectionContext;
import back.domain.gateway.exception.OpenClawGatewayException;
import back.domain.gateway.service.GatewayConnectionFailureResolver;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.entity.OrchestrationPlanStep;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.domain.orchestrator.repository.OrchestrationPlanStepRepository;
import back.domain.slack.event.SlackReplyRequestedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void run(Long workspaceId, Long planId) {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(planId);
        PlanExecutionContext planContext = preparePlan(workspaceId, planId);
        Map<String, StepExecutionSummary> completedSteps = new LinkedHashMap<>();
        OpenClawGatewayClient client = null;
        try {
            client = openClawGatewayClientFactory.create();
            OpenClawGatewayConnectionContext gatewayContext =
                    workspaceGatewayBindingService.getConnectionContext(workspaceId);
            client.connect(gatewayContext);
            for (StepExecutionContext stepContext : resolveExecutionOrder(planContext.steps())) {
                StepExecutionSummary summary = runStep(client, planContext, stepContext, completedSteps);
                if (summary.failed()) {
                    markPlanFailed(planContext, completedSteps, stepContext.stepKey(), summary);
                    return;
                }
                if (summary.canceled()) {
                    markPlanCanceled(planContext, completedSteps, stepContext.stepKey(), summary);
                    return;
                }
                completedSteps.put(stepContext.stepKey(), summary);
            }
            markPlanCompleted(planContext, completedSteps);
        } catch (RuntimeException exception) {
            markPlanFailed(
                    planContext,
                    completedSteps,
                    null,
                    StepExecutionSummary.failed(resolveFailureReason(exception), null));
            throw exception;
        } finally {
            if (client != null) {
                client.close();
            }
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
                    plan.getChatSessionId(),
                    plan.getTitle(),
                    plan.getOrchestratorAgentId(),
                    steps);
        }));
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
            return failStep(stepContext, resolveGatewayFailureReason(exception), null);
        } catch (RuntimeException exception) {
            return failStep(stepContext, resolveFailureReason(exception), null);
        }
    }

    private Agent resolveAgent(Long workspaceId, StepExecutionContext stepContext) {
        if (stepContext.agentId() != null) {
            Agent agent = agentRepository
                    .findByIdAndWorkspaceIdAndStatusNot(
                            stepContext.agentId(), workspaceId, AgentStatus.DISABLED)
                    .orElseThrow(() -> executionError("선택한 Worker Agent를 찾을 수 없습니다."));
            validateExecutableAgent(agent);
            return agent;
        }
        if (stepContext.agentName() != null) {
            Agent agent = agentRepository
                    .findByWorkspaceIdAndNameAndStatusNot(
                            workspaceId, stepContext.agentName(), AgentStatus.DISABLED)
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

    private void markPlanCompleted(
            PlanExecutionContext planContext, Map<String, StepExecutionSummary> completedSteps) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = getPlanOrThrow(planContext.workspaceId(), planContext.planId());
            plan.markCompleted();
            OrchestrationPlan savedPlan = orchestrationPlanRepository.save(plan);
            appendOrchestrationResultMessage(savedPlan, buildCompletedChatMessage(planContext, completedSteps));
            return savedPlan;
        }));
    }

    private void markPlanFailed(
            PlanExecutionContext planContext,
            Map<String, StepExecutionSummary> completedSteps,
            String failedStepKey,
            StepExecutionSummary failureSummary) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = getPlanOrThrow(planContext.workspaceId(), planContext.planId());
            plan.markFailed();
            OrchestrationPlan savedPlan = orchestrationPlanRepository.save(plan);
            appendOrchestrationResultMessage(
                    savedPlan,
                    buildStoppedChatMessage(
                            "Orchestration 실행이 실패했습니다.",
                            "FAILED",
                            "실패 step",
                            planContext,
                            completedSteps,
                            failedStepKey,
                            failureSummary));
            return savedPlan;
        }));
    }

    private void markPlanCanceled(
            PlanExecutionContext planContext,
            Map<String, StepExecutionSummary> completedSteps,
            String canceledStepKey,
            StepExecutionSummary cancelSummary) {
        requireTransactionResult(transactionOperations.execute(status -> {
            OrchestrationPlan plan = getPlanOrThrow(planContext.workspaceId(), planContext.planId());
            plan.markCanceled();
            OrchestrationPlan savedPlan = orchestrationPlanRepository.save(plan);
            appendOrchestrationResultMessage(
                    savedPlan,
                    buildStoppedChatMessage(
                            "Orchestration 실행이 취소되었습니다.",
                            "CANCELED",
                            "취소 step",
                            planContext,
                            completedSteps,
                            canceledStepKey,
                            cancelSummary));
            return savedPlan;
        }));
    }

    private void appendOrchestrationResultMessage(OrchestrationPlan plan, String content) {
        ChatMessage savedMessage = chatMessageRepository.save(ChatMessage.assistantForOrchestration(
                plan.getWorkspaceId(), plan.getChatSessionId(), plan.getId(), content));
        chatSessionRepository
                .findByIdAndWorkspaceId(plan.getChatSessionId(), plan.getWorkspaceId())
                .ifPresent(session -> recordSessionMessage(session, plan, savedMessage));
    }

    private void recordSessionMessage(ChatSession session, OrchestrationPlan plan, ChatMessage message) {
        session.recordMessage();
        chatSessionRepository.save(session);
        publishSlackResultIfNeeded(session, plan, message.getContent());
    }

    private void publishSlackResultIfNeeded(ChatSession session, OrchestrationPlan plan, String content) {
        if (session.getSource() != ChatSessionSource.SLACK
                || session.getSourceRef() == null
                || session.getSourceRef().isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new SlackReplyRequestedEvent(
                session.getSourceRef(),
                content,
                "slack-orchestration-plan-" + plan.getId() + "-" + plan.getStatus()));
    }

    private String buildCompletedChatMessage(
            PlanExecutionContext planContext, Map<String, StepExecutionSummary> completedSteps) {
        List<String> lines = new ArrayList<>();
        lines.add("Orchestration 실행이 완료되었습니다.");
        lines.add("- 계획: " + planContext.title());
        lines.add("- 상태: COMPLETED");
        lines.add("- 완료 step: " + completedSteps.size() + "/" + planContext.steps().size());
        appendCompletedStepLines(lines, completedSteps);
        appendNextActionLines(lines, completedSteps);
        return String.join(System.lineSeparator(), lines);
    }

    private String buildStoppedChatMessage(
            String headline,
            String status,
            String terminalStepLabel,
            PlanExecutionContext planContext,
            Map<String, StepExecutionSummary> completedSteps,
            String terminalStepKey,
            StepExecutionSummary terminalSummary) {
        List<String> lines = new ArrayList<>();
        lines.add(headline);
        lines.add("- 계획: " + planContext.title());
        lines.add("- 상태: " + status);
        lines.add("- 완료 step: " + completedSteps.size() + "/" + planContext.steps().size());
        if (terminalStepKey != null && !terminalStepKey.isBlank()) {
            lines.add("- " + terminalStepLabel + ": " + terminalStepKey);
        }
        lines.add("- 사유: " + fallbackText(terminalSummary.failureReason(), "알 수 없음"));
        appendCompletedStepLines(lines, completedSteps);
        return String.join(System.lineSeparator(), lines);
    }

    private void appendCompletedStepLines(List<String> lines, Map<String, StepExecutionSummary> completedSteps) {
        if (completedSteps.isEmpty()) {
            return;
        }
        lines.add("");
        lines.add("완료된 step");
        completedSteps.forEach((stepKey, summary) -> lines.add("- "
                + stepKey
                + ": "
                + fallbackText(summary.summary(), "요약 없음")
                + formatFilePaths(summary.filePaths())));
    }

    private void appendNextActionLines(List<String> lines, Map<String, StepExecutionSummary> completedSteps) {
        List<String> nextActions = completedSteps.values().stream()
                .flatMap(summary -> summary.nextActions().stream())
                .filter(action -> action != null && !action.isBlank())
                .distinct()
                .toList();
        if (nextActions.isEmpty()) {
            return;
        }
        lines.add("");
        lines.add("다음 작업");
        nextActions.forEach(action -> lines.add("- " + action));
    }

    private String fallbackText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
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

    private String resolveGatewayFailureReason(OpenClawGatewayException exception) {
        if ("gateway_rpc_timeout".equalsIgnoreCase(exception.gatewayErrorCode())) {
            return "Worker Agent 응답 시간이 초과되었습니다. OpenClaw에서 작업이 계속 실행 중인지 확인하고, "
                    + "필요하면 OPENCLAW_GATEWAY_CHAT_TIMEOUT 값을 늘려 주세요.";
        }
        return GatewayConnectionFailureResolver.resolveClientMessage(exception);
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
            Long chatSessionId,
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
