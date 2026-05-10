package back.domain.slack.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import back.domain.orchestrator.entity.OrchestratorSession;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.event.OrchestratorSessionFinishedEvent;
import back.domain.orchestrator.repository.OrchestratorSessionRepository;
import back.domain.slack.client.SlackClient;
import back.domain.slack.dto.request.SlackMessageReq;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        ArgumentCaptor<SlackMessageReq> captor = ArgumentCaptor.forClass(SlackMessageReq.class);
        verify(slackClient, times(1)).sendMessage(eq("xoxb-valid-token"), captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo("C456");
        assertThat(captor.getValue().threadTs()).isEqualTo("1715059800.123");
        assertThat(captor.getValue().text()).isEqualTo("성공 완료 메시지");
    }

    @Test
    @DisplayName("Slack reply 요청 이벤트를 받으면 원래 thread로 메시지를 전송한다")
    void onSlackReplyRequested_Success() {
        // given
        SlackReplyRequestedEvent event =
                new SlackReplyRequestedEvent("T123:C456:1715059800.123", "Agent 응답", "reply-1");
        SlackIntegration integration = mock(SlackIntegration.class);

        given(integration.getBotToken()).willReturn("xoxb-valid-token");
        given(integrationRepository.findFirstBySlackTeamId("T123")).willReturn(Optional.of(integration));

        // when
        slackReportListener.onSlackReplyRequested(event);

        // then
        ArgumentCaptor<SlackMessageReq> captor = ArgumentCaptor.forClass(SlackMessageReq.class);
        verify(slackClient).sendMessage(eq("xoxb-valid-token"), captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo("C456");
        assertThat(captor.getValue().threadTs()).isEqualTo("1715059800.123");
        assertThat(captor.getValue().text()).isEqualTo("Agent 응답");
    }

    @Test
    @DisplayName("같은 중복 방지 키를 가진 Slack reply 요청은 한 번만 전송한다")
    void onSlackReplyRequested_duplicateKeySendsOnce() {
        // given
        SlackReplyRequestedEvent event =
                new SlackReplyRequestedEvent("T123:C456:1715059800.123", "Agent 응답", "reply-1");
        SlackIntegration integration = mock(SlackIntegration.class);

        given(integration.getBotToken()).willReturn("xoxb-valid-token");
        given(integrationRepository.findFirstBySlackTeamId("T123")).willReturn(Optional.of(integration));

        // when
        slackReportListener.onSlackReplyRequested(event);
        slackReportListener.onSlackReplyRequested(event);

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
