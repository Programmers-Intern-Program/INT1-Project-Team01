package back.domain.orchestrator.event;

public record OrchestratorSessionFinishedEvent(
        Long sessionId,
        String message
) {
}