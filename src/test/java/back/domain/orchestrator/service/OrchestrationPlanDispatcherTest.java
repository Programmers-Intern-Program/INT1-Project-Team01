package back.domain.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestrationPlanDispatcherTest {

    @InjectMocks
    private OrchestrationPlanDispatcher orchestrationPlanDispatcher;

    @Mock
    private OrchestrationPlanRunner orchestrationPlanRunner;

    @Test
    @DisplayName("Orchestration Plan 실행을 Runner에 위임한다")
    void run_delegatesRunner() {
        // when
        orchestrationPlanDispatcher.run(1L, 2L);

        // then
        verify(orchestrationPlanRunner).run(1L, 2L);
    }

    @Test
    @DisplayName("Orchestration Plan 실행 중 예외가 발생하면 전파하지 않고 종료한다")
    void run_swallowsRunnerException() {
        // given
        willThrow(new RuntimeException("runner failed"))
                .given(orchestrationPlanRunner)
                .run(1L, 2L);

        // when & then
        assertThatCode(() -> orchestrationPlanDispatcher.run(1L, 2L))
                .doesNotThrowAnyException();
        verify(orchestrationPlanRunner).run(1L, 2L);
    }
}
