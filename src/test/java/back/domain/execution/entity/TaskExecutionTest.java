package back.domain.execution.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskExecutionTest {

    @Test
    @DisplayName("repositoryId는 null이면 실행 기록 생성 시 허용한다")
    void queued_nullRepositoryId_success() {
        // when
        TaskExecution execution = TaskExecution.queued(1L, 2L, 3L, "openclaw-agent-1", null, "ai/task-2");

        // then
        assertThat(execution.getRepositoryId()).isNull();
    }

    @Test
    @DisplayName("repositoryId는 값이 있으면 실행 기록 생성 시 양수만 허용한다")
    void queued_invalidRepositoryId_throwsException() {
        assertThatThrownBy(() -> TaskExecution.queued(1L, 2L, 3L, "openclaw-agent-1", -1L, "ai/task-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositoryId");
    }
}
