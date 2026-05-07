package back.domain.task.entity;

public enum SourceType {
    DASHBOARD,      // 사용자가 웹 대시보드에서 직접 생성한 Task
    SLACK,          // Slack 멘션 또는 명령어를 통해 생성된 Task
    GITHUB,         // GitHub Webhook, Issue, PR 이벤트 등을 통해 생성된 Task
    ORCHESTRATOR,    // Orchestrator가 사용자 요청을 분석한 뒤 자동 생성한 Task
    OTHER           // 기타 출처
}
