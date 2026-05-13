package back.domain.workspace.dto.response;

import java.time.LocalDateTime;

import back.domain.member.dto.response.MemberProfileSummaryRes;
import back.domain.workspace.enums.WorkspaceMemberRole;

public record WorkspaceMemberInfoRes(
        Long memberId,
        String name,
        String email,
        WorkspaceMemberRole role,
        LocalDateTime joinedAt,
        MemberProfileSummaryRes profile
) {
}
