package back.domain.task.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import back.domain.task.entity.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskResponseStatusMapperTest {

    @Test
    @DisplayName("Agent status 별칭을 TaskStatus로 변환한다")
    void fromAgentStatus() {
        assertThat(TaskResponseStatusMapper.fromAgentStatus("succeeded")).isEqualTo(TaskStatus.COMPLETED);
        assertThat(TaskResponseStatusMapper.fromAgentStatus("error")).isEqualTo(TaskStatus.FAILED);
        assertThat(TaskResponseStatusMapper.fromAgentStatus("cancelled")).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    @DisplayName("선택 상태값은 비어 있으면 null을 반환한다")
    void fromOptionalAgentStatus() {
        assertThat(TaskResponseStatusMapper.fromOptionalAgentStatus(null)).isNull();
        assertThat(TaskResponseStatusMapper.fromOptionalAgentStatus(" ")).isNull();
        assertThat(TaskResponseStatusMapper.fromOptionalAgentStatus("done")).isEqualTo(TaskStatus.COMPLETED);
    }
}
