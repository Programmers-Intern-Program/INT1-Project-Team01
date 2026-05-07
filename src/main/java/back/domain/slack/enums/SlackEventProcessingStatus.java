package back.domain.slack.enums;

/**
 * Slack 이벤트의 처리 상태를 나타냅니다.
 * 설계 문서 [5. SlackEventLog 모델] 기준
 */

 public enum SlackEventProcessingStatus {
    RECEIVED,
    PROCESSED,
    FAILED,
    IGNORED
}