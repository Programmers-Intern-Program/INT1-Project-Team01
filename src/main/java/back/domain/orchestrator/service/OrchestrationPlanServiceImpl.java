package back.domain.orchestrator.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import back.domain.agent.entity.Agent;
import back.domain.agent.entity.AgentStatus;
import back.domain.agent.repository.AgentRepository;
import back.domain.orchestrator.dto.request.OrchestrationPlanCreateCommand;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.entity.OrchestrationPlanStep;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrchestrationPlanServiceImpl implements OrchestrationPlanService {

    private static final Pattern STEP_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,80}");
    private static final String DEFAULT_PLAN_TITLE = "Orchestrator 작업 계획";

    private final OrchestrationPlanRepository orchestrationPlanRepository;
    private final AgentRepository agentRepository;

    @Override
    @Transactional
    public OrchestrationPlan createPlan(OrchestrationPlanCreateCommand command) {
        validateCommand(command);
        validateSteps(command.steps());
        validateAgents(command.workspaceId(), command.steps());
        validateDependencies(command.steps());

        OrchestrationPlan plan = OrchestrationPlan.create(
                command.workspaceId(),
                command.chatSessionId(),
                command.orchestratorAgentId(),
                command.userMessageId(),
                resolvePlanTitle(command),
                command.rawResponse());
        int sequenceNo = 1;
        for (OrchestrationPlanCreateCommand.StepCommand step : command.steps()) {
            plan.addStep(OrchestrationPlanStep.create(
                    plan,
                    sequenceNo++,
                    step.stepKey(),
                    step.agentId(),
                    step.agentName(),
                    step.category(),
                    step.title(),
                    step.prompt(),
                    step.dependsOn()));
        }
        return orchestrationPlanRepository.save(plan);
    }

    private void validateCommand(OrchestrationPlanCreateCommand command) {
        if (command == null) {
            throw invalidPlan("계획 응답이 비어 있습니다.");
        }
        requirePositive(command.workspaceId(), "workspaceId");
        requirePositive(command.chatSessionId(), "chatSessionId");
        requirePositive(command.orchestratorAgentId(), "orchestratorAgentId");
        requirePositive(command.userMessageId(), "userMessageId");
        if (command.rawResponse() == null || command.rawResponse().isBlank()) {
            throw invalidPlan("원본 계획 응답이 비어 있습니다.");
        }
    }

    private void validateSteps(List<OrchestrationPlanCreateCommand.StepCommand> steps) {
        if (steps.isEmpty()) {
            throw invalidPlan("실행 단계가 없습니다.");
        }
        Set<String> stepKeys = new HashSet<>();
        for (OrchestrationPlanCreateCommand.StepCommand step : steps) {
            validateStep(step);
            if (!stepKeys.add(step.stepKey())) {
                throw invalidPlan("중복된 stepKey가 있습니다: " + step.stepKey());
            }
        }
    }

    private void validateStep(OrchestrationPlanCreateCommand.StepCommand step) {
        if (step == null) {
            throw invalidPlan("비어 있는 실행 단계가 있습니다.");
        }
        if (step.stepKey() == null || !STEP_KEY_PATTERN.matcher(step.stepKey()).matches()) {
            throw invalidPlan("stepKey는 영문, 숫자, '.', '_', '-'만 사용할 수 있습니다.");
        }
        if (step.agentId() == null && isBlank(step.agentName()) && step.category() == null) {
            throw invalidPlan("각 단계에는 agentId, agentName, category 중 하나 이상이 필요합니다.");
        }
        if (isBlank(step.title())) {
            throw invalidPlan("각 단계에는 title이 필요합니다. stepKey=" + step.stepKey());
        }
        if (isBlank(step.prompt())) {
            throw invalidPlan("각 단계에는 prompt가 필요합니다. stepKey=" + step.stepKey());
        }
    }

    private void validateAgents(Long workspaceId, List<OrchestrationPlanCreateCommand.StepCommand> steps) {
        for (OrchestrationPlanCreateCommand.StepCommand step : steps) {
            if (step.agentId() == null) {
                continue;
            }
            Agent agent = agentRepository.findByIdAndWorkspaceId(step.agentId(), workspaceId)
                    .orElseThrow(() -> invalidPlan("존재하지 않는 agentId가 포함되어 있습니다: " + step.agentId()));
            if (agent.getStatus() != AgentStatus.READY || isBlank(agent.getOpenClawAgentId())) {
                throw invalidPlan("READY 상태가 아닌 Agent가 포함되어 있습니다: " + step.agentId());
            }
        }
    }

    private void validateDependencies(List<OrchestrationPlanCreateCommand.StepCommand> steps) {
        Set<String> stepKeys = new HashSet<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (OrchestrationPlanCreateCommand.StepCommand step : steps) {
            stepKeys.add(step.stepKey());
            graph.put(step.stepKey(), step.dependsOn());
        }
        for (OrchestrationPlanCreateCommand.StepCommand step : steps) {
            for (String dependsOn : step.dependsOn()) {
                if (!stepKeys.contains(dependsOn)) {
                    throw invalidPlan("존재하지 않는 dependsOn stepKey가 있습니다: " + dependsOn);
                }
                if (step.stepKey().equals(dependsOn)) {
                    throw invalidPlan("자기 자신을 dependsOn으로 지정할 수 없습니다: " + step.stepKey());
                }
            }
        }
        detectCycle(graph);
    }

    private void detectCycle(Map<String, List<String>> graph) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String stepKey : graph.keySet()) {
            if (hasCycle(stepKey, graph, visiting, visited)) {
                throw invalidPlan("dependsOn에 순환 참조가 있습니다.");
            }
        }
    }

    private boolean hasCycle(
            String stepKey,
            Map<String, List<String>> graph,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(stepKey)) {
            return false;
        }
        if (!visiting.add(stepKey)) {
            return true;
        }
        for (String dependsOn : graph.getOrDefault(stepKey, List.of())) {
            if (hasCycle(dependsOn, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(stepKey);
        visited.add(stepKey);
        return false;
    }

    private String resolvePlanTitle(OrchestrationPlanCreateCommand command) {
        if (command.title() == null || command.title().isBlank()) {
            return DEFAULT_PLAN_TITLE;
        }
        String title = command.title().trim();
        if (title.length() <= 200) {
            return title;
        }
        return title.substring(0, 200);
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw invalidPlan(fieldName + " 값이 올바르지 않습니다.");
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ServiceException invalidPlan(String detail) {
        return new ServiceException(
                CommonErrorCode.BAD_REQUEST_STATE,
                "[OrchestrationPlanServiceImpl] invalid orchestration plan. detail=" + detail,
                "Orchestrator 계획 응답 형식이 올바르지 않습니다. " + detail);
    }
}
