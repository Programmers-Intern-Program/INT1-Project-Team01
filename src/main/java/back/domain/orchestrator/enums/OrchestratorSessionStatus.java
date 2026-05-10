package back.domain.orchestrator.enums;

/**
 * Orchestrator Session의 진행 상태를 정의합니다.
 */
public enum OrchestratorSessionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}