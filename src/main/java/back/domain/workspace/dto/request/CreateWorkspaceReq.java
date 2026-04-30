package back.domain.workspace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceReq(
        @NotBlank(message = "워크스페이스 이름은 필수입니다.")
        @Size(max = 100, message = "워크스페이스 이름은 100자 이하여야 합니다.")
        String name,
        @Size(max = 500, message = "워크스페이스 설명은 500자 이하여야 합니다.")
        String description
) {
}
