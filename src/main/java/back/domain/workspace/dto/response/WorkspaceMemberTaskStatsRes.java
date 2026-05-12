package back.domain.workspace.dto.response;

public record WorkspaceMemberTaskStatsRes(
        Long memberId,
        String memberName,
        int rank,
        int taskCount,
        int completedTaskCount,
        int runningTaskCount,
        int failedTaskCount,
        int waitingUserTaskCount,
        int healthScore,
        int flowScore,
        int impactScore,
        int totalScore
) {
}
