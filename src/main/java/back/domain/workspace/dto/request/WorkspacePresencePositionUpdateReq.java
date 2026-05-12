package back.domain.workspace.dto.request;

import back.domain.workspace.dto.WorkspacePresencePosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkspacePresencePositionUpdateReq(
        @NotNull(message = "position은 필수입니다.") @Valid WorkspacePresencePosition position,
        @Size(max = 30, message = "status는 30자 이하여야 합니다.") String status) {}
