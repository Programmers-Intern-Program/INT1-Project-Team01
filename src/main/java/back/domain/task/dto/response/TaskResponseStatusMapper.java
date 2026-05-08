package back.domain.task.dto.response;

import back.domain.execution.service.AgentExecutionStatus;
import back.domain.task.entity.TaskStatus;

final class TaskResponseStatusMapper {

    private TaskResponseStatusMapper() {
    }

    static TaskStatus fromAgentStatus(String value) {
        if (value == null || value.isBlank()) {
            return TaskStatus.FAILED;
        }
        return switch (AgentExecutionStatus.from(value)) {
            case COMPLETED -> TaskStatus.COMPLETED;
            case FAILED -> TaskStatus.FAILED;
            case CANCELED -> TaskStatus.CANCELED;
        };
    }

    static TaskStatus fromOptionalAgentStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return fromAgentStatus(value);
    }
}
