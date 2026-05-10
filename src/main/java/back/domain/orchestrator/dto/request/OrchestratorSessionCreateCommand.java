package back.domain.orchestrator.dto.request;

import back.domain.orchestrator.enums.OrchestratorSessionSource;
import lombok.Builder;

/**
 * Orchestrator Session 생성을 위해 서비스 계층으로 전달되는 Command DTO입니다.
 */
@Builder
public record OrchestratorSessionCreateCommand(
        Long workspaceId,
        Long requestedByMemberId,
        OrchestratorSessionSource source,
        String sourceRef,
        String userMessage
) {
}