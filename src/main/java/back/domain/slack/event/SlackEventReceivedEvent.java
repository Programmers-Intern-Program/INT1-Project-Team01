package back.domain.slack.event;

/**
 * Slack 이벤트 수신 로그 저장 후 비동기 처리를 트리거하기 위한 내부 이벤트입니다.
 */
public record SlackEventReceivedEvent(Long eventLogId) {
}