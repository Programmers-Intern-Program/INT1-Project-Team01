package back.domain.slack.event;

import back.domain.orchestrator.entity.OrchestratorSession;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.event.OrchestratorSessionFinishedEvent;
import back.domain.orchestrator.repository.OrchestratorSessionRepository;
import back.domain.slack.client.SlackClient;
import back.domain.slack.dto.request.SlackMessageReq;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackReportListenerTest {

    @Mock
    private OrchestratorSessionRepository sessionRepository;
    @Mock
    private SlackIntegrationRepository integrationRepository;
    @Mock
    private SlackClient slackClient;

    @InjectMocks
    private SlackReportListener slackReportListener;

    @Test
    @DisplayName("SLACK 출처의 정상 세션 완료 이벤트 수신 시 메시지를 전송한다")
    void onSessionFinished_Success() {
        // given
        OrchestratorSessionFinishedEvent event = new OrchestratorSessionFinishedEvent(1L, "성공 완료 메시지");
        OrchestratorSession session = mock(OrchestratorSession.class);
        SlackIntegration integration = mock(SlackIntegration.class);

        given(session.getSource()).willReturn(OrchestratorSessionSource.SLACK);
        given(session.getSourceRef()).willReturn("T123:C456:1715059800.123");
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        given(integration.getBotToken()).willReturn("xoxb-valid-token");
        given(integrationRepository.findFirstBySlackTeamId("T123")).willReturn(Optional.of(integration));

        // when
        slackReportListener.onOrchestratorSessionFinished(event);

        // then
        verify(slackClient, times(1)).sendMessage(eq("xoxb-valid-token"), any(SlackMessageReq.class));
    }

    @Test
    @DisplayName("출처가 SLACK이 아니면 메시지를 전송하지 않는다")
    void onSessionFinished_NotSlackSource_IgnoresEvent() {
        // given
        OrchestratorSessionFinishedEvent event = new OrchestratorSessionFinishedEvent(1L, "메시지");
        OrchestratorSession session = mock(OrchestratorSession.class);

        given(session.getSource()).willReturn(OrchestratorSessionSource.WEB);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        // when
        slackReportListener.onOrchestratorSessionFinished(event);

        // then
        verify(integrationRepository, never()).findFirstBySlackTeamId(any());
        verify(slackClient, never()).sendMessage(any(), any());
    }

    @Test
    @DisplayName("sourceRef 형식이 올바르지 않으면 예외를 잡고 로그를 남기며 전송하지 않는다")
    void onSessionFinished_InvalidSourceRef_CatchException() {
        // given
        OrchestratorSessionFinishedEvent event = new OrchestratorSessionFinishedEvent(1L, "메시지");
        OrchestratorSession session = mock(OrchestratorSession.class);

        given(session.getSource()).willReturn(OrchestratorSessionSource.SLACK);
        given(session.getSourceRef()).willReturn("T123:C456");
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        // when
        slackReportListener.onOrchestratorSessionFinished(event);

        // then
        verify(integrationRepository, never()).findFirstBySlackTeamId(any());
        verify(slackClient, never()).sendMessage(any(), any());
    }
}