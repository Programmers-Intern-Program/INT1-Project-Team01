package back.domain.execution.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskExecutionRunCommandTest {

    @Test
    @DisplayName("repositoryId는 null이면 허용한다")
    void create_nullRepositoryId_success() {
        // when
        TaskExecutionRunCommand command = new TaskExecutionRunCommand(1L, 2L, null, null, "작업 실행", false);

        // then
        assertThat(command.repositoryId()).isNull();
    }

    @Test
    @DisplayName("assignedAgentId는 null이면 허용한다")
    void create_nullAssignedAgentId_success() {
        // when
        TaskExecutionRunCommand command = new TaskExecutionRunCommand(1L, 2L, null, 3L, "작업 실행", false);

        // then
        assertThat(command.assignedAgentId()).isNull();
    }

    @Test
    @DisplayName("assignedAgentId는 값이 있으면 양수만 허용한다")
    void create_invalidAssignedAgentId_throwsException() {
        assertThatThrownBy(() -> new TaskExecutionRunCommand(1L, 2L, 0L, 3L, "작업 실행", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignedAgentId");
    }

    @Test
    @DisplayName("repositoryId는 값이 있으면 양수만 허용한다")
    void create_invalidRepositoryId_throwsException() {
        assertThatThrownBy(() -> new TaskExecutionRunCommand(1L, 2L, 3L, 0L, "작업 실행", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repositoryId");
    }
}
