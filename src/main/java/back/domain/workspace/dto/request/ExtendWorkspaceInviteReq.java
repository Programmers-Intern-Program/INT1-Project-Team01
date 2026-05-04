package back.domain.workspace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ExtendWorkspaceInviteReq(
        @Min(value = 1, message = "초대 링크 연장일은 1일 이상이어야 합니다.")
        @Max(value = 30, message = "초대 링크 연장일은 30일 이하여야 합니다.")
        Integer additionalDays
) {
}
