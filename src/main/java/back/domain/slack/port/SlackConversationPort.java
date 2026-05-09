package back.domain.slack.port;

/**
 * Slack 이벤트에서 추출한 사용자 메시지를 공통 Agent 대화 흐름으로 전달하는 포트입니다.
 */
public interface SlackConversationPort {

    /**
     * Slack thread 기준 메시지를 Agent 대화로 전달합니다.
     *
     * @param workspaceId 내부 워크스페이스 ID
     * @param sourceRef   Slack thread 참조값(teamId:channelId:threadTs)
     * @param text        멘션 태그가 제거된 사용자 메시지
     * @return Agent가 반환한 최종 응답 텍스트
     */
    String sendMessage(Long workspaceId, String sourceRef, String text);
}
