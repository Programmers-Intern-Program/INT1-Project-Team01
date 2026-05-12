package back.domain.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspacePresencePosition(
        @NotBlank(message = "left는 필수입니다.") @Size(max = 30, message = "left는 30자 이하여야 합니다.") String left,
        @NotBlank(message = "top은 필수입니다.") @Size(max = 30, message = "top은 30자 이하여야 합니다.") String top) {}
