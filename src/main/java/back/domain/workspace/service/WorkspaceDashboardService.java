package back.domain.workspace.service;

import back.domain.workspace.dto.response.WorkspaceDashboardSummaryRes;

public interface WorkspaceDashboardService {

    /**
     * 워크스페이스 대시보드 요약 정보를 조회합니다.
     *
     * @param workspaceId 조회할 워크스페이스 ID
     * @param memberId    요청자의 회원 ID
     * @return Agent/Task 상태 집계와 최근 리포트/로그
     */
    WorkspaceDashboardSummaryRes getSummary(long workspaceId, long memberId);
}
