package back.domain.task.dto.request;

import back.domain.task.entity.TaskStatus;

public record AgentReportSaveRequest(
        TaskStatus status,
        String summary,
        String detail,
        String recommendedAction
) {
}