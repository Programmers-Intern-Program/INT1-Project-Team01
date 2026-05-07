package back.domain.task.dto.request;

import back.domain.task.entity.SourceType;
import back.domain.task.entity.TaskPriority;
import back.domain.task.entity.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskRunRequest(
        @NotBlank(message = "Task 제목은 필수입니다.")
        @Size(max = 100, message = "Task 제목은 100자 이하여야 합니다.")
        String title,

        @Size(max = 1000, message = "Task 설명은 1000자 이하여야 합니다.")
        String description,

        @NotNull(message = "Task 타입은 필수입니다.")
        TaskType taskType,

        TaskPriority priority,

        Long assignedAgentId,

        Long repositoryId,

        SourceType sourceType,

        @Size(max = 255, message = "sourceId는 255자 이하여야 합니다.")
        String sourceId,

        @Size(max = 2000, message = "원본 요청은 2000자 이하여야 합니다.")
        String originalRequest,

        Boolean createPr
) {

    public boolean shouldCreatePr() {
        return Boolean.TRUE.equals(createPr);
    }
}
