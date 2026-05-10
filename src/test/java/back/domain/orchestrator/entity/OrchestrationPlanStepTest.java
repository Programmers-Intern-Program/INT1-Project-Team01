package back.domain.orchestrator.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import back.domain.agent.entity.AgentCategory;

class OrchestrationPlanStepTest {

    @Test
    @DisplayName("기존 데이터의 Step status가 null이면 PENDING으로 취급한다")
    void getStatus_nullStatus_returnsPending() {
        // given
        OrchestrationPlan plan = OrchestrationPlan.create(1L, 2L, 3L, 4L, "계획", "{}");
        OrchestrationPlanStep step = OrchestrationPlanStep.create(
                plan,
                1,
                "backend-1",
                null,
                "backend-agent",
                AgentCategory.BACKEND,
                "백엔드 작업",
                "백엔드 작업을 진행하세요.",
                List.of());
        ReflectionTestUtils.setField(step, "status", null);

        // when & then
        assertThat(step.getStatus()).isEqualTo(OrchestrationPlanStepStatus.PENDING);
    }
}
