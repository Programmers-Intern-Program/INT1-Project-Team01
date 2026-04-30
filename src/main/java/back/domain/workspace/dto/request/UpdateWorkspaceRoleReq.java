package back.domain.workspace.dto.request;

import back.domain.workspace.enums.WorkspaceMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceRoleReq(
        @NotNull(message = "워크스페이스 멤버 역할은 필수입니다.")
        WorkspaceMemberRole role
) {
}
