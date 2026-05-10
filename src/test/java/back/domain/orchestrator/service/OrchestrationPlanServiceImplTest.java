package back.domain.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import back.domain.agent.entity.AgentCategory;
import back.domain.agent.repository.AgentRepository;
import back.domain.orchestrator.dto.request.OrchestrationPlanCreateCommand;
import back.domain.orchestrator.entity.OrchestrationPlan;
import back.domain.orchestrator.repository.OrchestrationPlanRepository;
import back.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestrationPlanServiceImplTest {

    @Mock
    private OrchestrationPlanRepository orchestrationPlanRepository;

    @Mock
    private AgentRepository agentRepository;

    private OrchestrationPlanServiceImpl orchestrationPlanService;

    @BeforeEach
    void setUp() {
        orchestrationPlanService = new OrchestrationPlanServiceImpl(orchestrationPlanRepository, agentRepository);
    }

    @Test
    @DisplayName("유효한 Orchestration 계획은 Plan과 Step으로 조립해 저장한다")
    void createPlan_validCommand_savesPlanWithSteps() {
        // given
        given(orchestrationPlanRepository.save(any(OrchestrationPlan.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        OrchestrationPlanCreateCommand command = command(List.of(
                step("backend-1", AgentCategory.BACKEND, List.of()),
                step("frontend-1", AgentCategory.FRONTEND, List.of("backend-1"))));

        // when
        OrchestrationPlan plan = orchestrationPlanService.createPlan(command);

        // then
        assertThat(plan.getTitle()).isEqualTo("회원가입 기능 구현 계획");
        assertThat(plan.getSteps()).hasSize(2);
        assertThat(plan.getSteps().getFirst().getStepKey()).isEqualTo("backend-1");
        assertThat(plan.getSteps().getLast().getDependsOnStepKeys()).containsExactly("backend-1");
        verify(orchestrationPlanRepository).save(plan);
    }

    @Test
    @DisplayName("stepKey가 중복되면 계획을 저장하지 않고 예외를 던진다")
    void createPlan_duplicateStepKey_throwsException() {
        // given
        OrchestrationPlanCreateCommand command = command(List.of(
                step("backend-1", AgentCategory.BACKEND, List.of()),
                step("backend-1", AgentCategory.FRONTEND, List.of())));

        // when & then
        assertThatThrownBy(() -> orchestrationPlanService.createPlan(command))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getClientMessage()).contains("중복된 stepKey"));
    }

    @Test
    @DisplayName("dependsOn 순환 참조가 있으면 계획을 저장하지 않고 예외를 던진다")
    void createPlan_cyclicDependsOn_throwsException() {
        // given
        OrchestrationPlanCreateCommand command = command(List.of(
                step("backend-1", AgentCategory.BACKEND, List.of("frontend-1")),
                step("frontend-1", AgentCategory.FRONTEND, List.of("backend-1"))));

        // when & then
        assertThatThrownBy(() -> orchestrationPlanService.createPlan(command))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getClientMessage()).contains("순환 참조"));
    }

    private OrchestrationPlanCreateCommand command(List<OrchestrationPlanCreateCommand.StepCommand> steps) {
        return new OrchestrationPlanCreateCommand(
                1L,
                2L,
                3L,
                4L,
                "회원가입 기능 구현 계획",
                "{\"intent\":\"ORCHESTRATE\"}",
                steps);
    }

    private OrchestrationPlanCreateCommand.StepCommand step(
            String stepKey,
            AgentCategory category,
            List<String> dependsOn) {
        return new OrchestrationPlanCreateCommand.StepCommand(
                stepKey,
                null,
                category.name().toLowerCase() + "-agent",
                category,
                stepKey + " 작업",
                stepKey + " 작업을 진행하세요.",
                dependsOn);
    }
}
