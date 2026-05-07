package back.domain.orchestrator.event;

/**
 * Orchestrator Session이 DB에 성공적으로 저장(PENDING 상태)되었을 때 발행되는 이벤트입니다.
 */
public record OrchestratorSessionCreatedEvent(
        Long sessionId
) {
}