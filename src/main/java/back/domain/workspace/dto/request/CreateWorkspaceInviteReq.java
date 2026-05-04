package back.domain.workspace.dto.request;

import back.domain.workspace.enums.WorkspaceMemberRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateWorkspaceInviteReq(
        @Min(value = 1, message = "초대 링크 만료일은 1일 이상이어야 합니다.")
        @Max(value = 30, message = "초대 링크 만료일은 30일 이하여야 합니다.")
        Integer expiresInDays,
        WorkspaceMemberRole role
) {
}
