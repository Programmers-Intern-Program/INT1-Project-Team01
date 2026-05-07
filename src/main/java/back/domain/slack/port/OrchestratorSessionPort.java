package back.domain.slack.port;

/**
 * Slack 이벤트에서 추출된 데이터를 바탕으로 AI Orchestrator Session 생성을 요청하는 포트입니다.
 * Slack 도메인은 이 인터페이스에만 의존하며, 실제 구현체는 Orchestrator/Task 도메인에서 주입(DI)합니다.
 */
public interface OrchestratorSessionPort {

    /**
     * AI 작업을 위한 새로운 세션을 생성합니다.
     *
     * @param teamId    Slack 워크스페이스 ID
     * @param channelId Slack 채널 ID
     * @param targetTs  스레드를 식별할 수 있는 타임스탬프 (threadTs 또는 ts)
     * @param text      멘션 태그가 제거된 순수 명령어 텍스트
     */
    void createSession(String teamId, String channelId, String targetTs, String text);
}