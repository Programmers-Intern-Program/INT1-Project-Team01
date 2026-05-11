package back.domain.orchestrator.service;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.entity.OrchestratorSession;
import back.domain.orchestrator.enums.OrchestratorSessionStatus;
import back.domain.orchestrator.event.OrchestratorSessionCreatedEvent;
import back.domain.orchestrator.repository.OrchestratorSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorSessionServiceImpl implements OrchestratorSessionService {

    private final OrchestratorSessionRepository orchestratorSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrchestratorSession createSession(OrchestratorSessionCreateCommand command) {
        log.debug("Orchestrator Session 생성 요청 수신. source: {}", command.source());

        OrchestratorSession session = createPendingSessionEntity(command);
        OrchestratorSession savedSession = orchestratorSessionRepository.save(session);
        log.info(
                "Orchestrator Session 저장 완료. sessionId: {}, status: {}",
                savedSession.getId(), savedSession.getStatus()
        );

        eventPublisher.publishEvent(
                new OrchestratorSessionCreatedEvent(savedSession.getId())
        );

        return savedSession;
    }

    /**
     * 전달받은 Command를 바탕으로 PENDING 상태의 엔티티를 조립합니다.
     */
    private OrchestratorSession createPendingSessionEntity(OrchestratorSessionCreateCommand command) {
        return OrchestratorSession.builder()
                .workspaceId(command.workspaceId())
                .requestedByMemberId(command.requestedByMemberId())
                .source(command.source())
                .sourceRef(command.sourceRef())
                .userMessage(command.userMessage())
                .status(OrchestratorSessionStatus.PENDING)
                .build();
    }
}