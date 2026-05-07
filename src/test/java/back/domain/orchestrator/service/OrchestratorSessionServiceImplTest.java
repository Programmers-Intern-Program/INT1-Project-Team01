package back.domain.orchestrator.service;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.entity.OrchestratorSession;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.enums.OrchestratorSessionStatus;
import back.domain.orchestrator.event.OrchestratorSessionCreatedEvent;
import back.domain.orchestrator.repository.OrchestratorSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestratorSessionServiceImplTest {

    @InjectMocks
    private OrchestratorSessionServiceImpl orchestratorSessionService;

    @Mock
    private OrchestratorSessionRepository orchestratorSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Command를 전달받아 PENDING 상태의 세션을 DB에 저장하고 생성 이벤트를 발행한다.")
    void createSession_Success() {
        // given
        OrchestratorSessionCreateCommand command = OrchestratorSessionCreateCommand.builder()
                .workspaceId(1L)
                .requestedByMemberId(2L)
                .source(OrchestratorSessionSource.SLACK)
                .sourceRef("T123:C123:1000.000")
                .userMessage("로그인 API 구현해줘")
                .build();

        // Repository 저장 시 반환될 Mock 엔티티 세팅
        OrchestratorSession savedSession = OrchestratorSession.builder()
                .workspaceId(1L)
                .requestedByMemberId(2L)
                .source(OrchestratorSessionSource.SLACK)
                .sourceRef("T123:C123:1000.000")
                .userMessage("로그인 API 구현해줘")
                .status(OrchestratorSessionStatus.PENDING)
                .build();

        ReflectionTestUtils.setField(savedSession, "id", 100L);

        when(orchestratorSessionRepository.save(any(OrchestratorSession.class))).thenReturn(savedSession);

        // when
        OrchestratorSession result = orchestratorSessionService.createSession(command);

        // then
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(OrchestratorSessionStatus.PENDING);

        ArgumentCaptor<OrchestratorSession> sessionCaptor = ArgumentCaptor.forClass(OrchestratorSession.class);
        verify(orchestratorSessionRepository).save(sessionCaptor.capture());

        OrchestratorSession capturedSession = sessionCaptor.getValue();
        assertThat(capturedSession.getWorkspaceId()).isEqualTo(1L);
        assertThat(capturedSession.getSource()).isEqualTo(OrchestratorSessionSource.SLACK);
        assertThat(capturedSession.getStatus()).isEqualTo(OrchestratorSessionStatus.PENDING);

        ArgumentCaptor<OrchestratorSessionCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrchestratorSessionCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(100L);
    }
}