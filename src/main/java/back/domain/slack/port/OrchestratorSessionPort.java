package back.domain.slack.port;

/**
 * Slack 이벤트에서 추출된 데이터를 바탕으로 AI Orchestrator Session 생성을 요청하는 포트입니다.
 * Slack 도메인은 이 인터페이스에만 의존하며, 실제 구현체는 Orchestrator/Task 도메인에서 주입(DI)합니다.
 */
public interface OrchestratorSessionPort {

    /**
     * AI 작업을 위한 새로운 세션을 생성합니다.
     *
     * @param workspaceId 내부 워크스페이스 ID (SlackIntegration을 통해 식별)
     * @param sourceRef   이벤트 출처 참조값 (team_id:channel_id:ts 형식)
     * @param targetTs    스레드를 식별할 수 있는 타임스탬프 (threadTs 또는 ts)
     * @param text        멘션 태그가 제거된 순수 명령어 텍스트
     */
    void createSession(Long workspaceId, String sourceRef, String targetTs, String text);
}