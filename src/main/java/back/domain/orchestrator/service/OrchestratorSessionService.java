package back.domain.orchestrator.service;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.entity.OrchestratorSession;

/**
 * Orchestrator Session(사용자 요청 관리 단위)의 비즈니스 로직을 처리하는 서비스입니다.
 */
public interface OrchestratorSessionService {

    /**
     * 새로운 Orchestrator Session을 PENDING 상태로 생성하고 DB에 저장합니다.
     * 저장 직후 내부 이벤트를 발행하여 비동기 AI 작업(OpenClaw 호출)이 이어지도록 유도합니다.
     *
     * @param command 세션 생성을 위한 데이터가 담긴 DTO
     * @return 저장된 OrchestratorSession 엔티티
     */
    OrchestratorSession createSession(OrchestratorSessionCreateCommand command);
}