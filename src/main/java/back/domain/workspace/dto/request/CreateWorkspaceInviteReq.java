package back.domain.workspace.dto.request;

import back.domain.workspace.enums.WorkspaceMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceInviteReq(
        @Min(value = 1, message = "초대 링크 만료일은 1일 이상이어야 합니다.")
        @Max(value = 30, message = "초대 링크 만료일은 30일 이하여야 합니다.")
        Integer expiresInDays,
        WorkspaceMemberRole role,
        @Email(message = "초대 대상 이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "초대 대상 이메일은 255자 이하여야 합니다.")
        String targetEmail
) {
    public CreateWorkspaceInviteReq(Integer expiresInDays, WorkspaceMemberRole role) {
        this(expiresInDays, role, null);
    }
}
