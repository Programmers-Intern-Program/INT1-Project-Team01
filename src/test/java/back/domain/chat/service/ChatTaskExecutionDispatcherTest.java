package back.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import back.domain.task.service.TaskRunService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatTaskExecutionDispatcherTest {

    @InjectMocks
    private ChatTaskExecutionDispatcher chatTaskExecutionDispatcher;

    @Mock
    private TaskRunService taskRunService;

    @Test
    @DisplayName("채팅 Task 실행을 TaskRunService에 위임한다")
    void run_delegatesTaskRunService() {
        // when
        chatTaskExecutionDispatcher.run(1L, 2L, true);

        // then
        verify(taskRunService).runTask(1L, 2L, true, null);
    }

    @Test
    @DisplayName("채팅 Task 실행 시 OpenClaw sessionKey override를 TaskRunService에 전달한다")
    void run_withOpenClawSessionKeyOverride_delegatesTaskRunService() {
        // when
        chatTaskExecutionDispatcher.run(1L, 2L, true, "workspace-1-agent-1-chat-fixed");

        // then
        verify(taskRunService).runTask(1L, 2L, true, "workspace-1-agent-1-chat-fixed");
    }

    @Test
    @DisplayName("Task 실행 중 예외가 발생하면 전파하지 않고 종료한다")
    void run_swallowsRunnerException() {
        // given
        willThrow(new RuntimeException("runner failed"))
                .given(taskRunService)
                .runTask(1L, 2L, false, null);

        // when & then
        assertThatCode(() -> chatTaskExecutionDispatcher.run(1L, 2L, false))
                .doesNotThrowAnyException();
        verify(taskRunService).runTask(1L, 2L, false, null);
    }
}
