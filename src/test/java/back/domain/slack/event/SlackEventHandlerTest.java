package back.domain.slack.event;

import back.domain.slack.entity.SlackEventLog;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.port.SlackConversationPort;
import back.domain.slack.repository.SlackEventLogRepository;
import back.domain.workspace.entity.Workspace;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackEventHandlerTest {

    private SlackEventHandler slackEventHandler;

    @Mock
    private SlackEventLogRepository slackEventLogRepository;

    @Mock
    private SlackConversationPort slackConversationPort;

    private SlackEventLog mockEventLog;
    private final Long eventLogId = 1L;

    @BeforeEach
    void setUp() {
        slackEventHandler = new SlackEventHandler(
                slackEventLogRepository,
                slackConversationPort,
                new SlackAgentCommandParser(),
                new JsonMapper());
        mockEventLog = mock(SlackEventLog.class);
    }

    private void givenIntegrationExists() {
        Workspace mockWorkspace = mock(Workspace.class);
        when(mockWorkspace.getId()).thenReturn(1L);

        SlackIntegration mockIntegration = mock(SlackIntegration.class);
        when(mockIntegration.getWorkspace()).thenReturn(mockWorkspace);

        when(mockEventLog.getIntegration()).thenReturn(mockIntegration);
    }

    @Test
    @DisplayName("멘션 태그가 포함된 메시지가 오면 태그를 제거하고 채팅 Port를 호출한 뒤 PROCESSED 처리한다.")
    void handleSlackEvent_Success() {
        // given
        givenIntegrationExists();
        String rawPayload = """
                {
                  "team_id": "T123",
                  "event": {
                    "channel": "C123",
                    "text": "<@U12345> 로그인 API 구현해줘",
                    "ts": "1000.000",
                    "thread_ts": "999.000"
                  }
                }
                """;
        when(slackEventLogRepository.findByIdWithIntegrationAndWorkspace(eventLogId))
                .thenReturn(Optional.of(mockEventLog));
        when(mockEventLog.getRawPayload()).thenReturn(rawPayload);

        // when
        slackEventHandler.handleSlackEvent(new SlackEventReceivedEvent(eventLogId));

        // then
        verify(slackConversationPort).sendMessage(1L, "T123:C123:999.000", null, "로그인 API 구현해줘");
        verify(mockEventLog).markAsProcessed();
        verify(slackEventLogRepository).save(mockEventLog);
    }

    @Test
    @DisplayName("/agent 문법이 포함된 메시지는 Agent 이름과 실행 메시지를 분리해 Port에 전달한다.")
    void handleSlackEvent_agentCommandParsesAgentName() {
        // given
        givenIntegrationExists();
        String rawPayload = """
                {
                  "team_id": "T123",
                  "event": {
                    "channel": "C123",
                    "text": "<@U12345> /agent backend-agent 로그인 API 구현해줘",
                    "ts": "1000.000",
                    "thread_ts": "999.000"
                  }
                }
                """;
        when(slackEventLogRepository.findByIdWithIntegrationAndWorkspace(eventLogId))
                .thenReturn(Optional.of(mockEventLog));
        when(mockEventLog.getRawPayload()).thenReturn(rawPayload);

        // when
        slackEventHandler.handleSlackEvent(new SlackEventReceivedEvent(eventLogId));

        // then
        verify(slackConversationPort)
                .sendMessage(1L, "T123:C123:999.000", "backend-agent", "로그인 API 구현해줘");
        verify(mockEventLog).markAsProcessed();
        verify(slackEventLogRepository).save(mockEventLog);
    }

    @Test
    @DisplayName("멘션 태그만 있고 실제 텍스트가 없으면 작업을 무시(IGNORED)한다.")
    void handleSlackEvent_IgnoredWhenEmptyText() {
        // given
        givenIntegrationExists();
        String rawPayload = """
                {
                  "team_id": "T123",
                  "event": {
                    "channel": "C123",
                    "text": "<@U12345>    ",
                    "ts": "1000.000"
                  }
                }
                """; // 텍스트에 공백과 멘션만 있음
        when(slackEventLogRepository.findByIdWithIntegrationAndWorkspace(eventLogId))
                .thenReturn(Optional.of(mockEventLog));
        when(mockEventLog.getRawPayload()).thenReturn(rawPayload);

        // when
        slackEventHandler.handleSlackEvent(new SlackEventReceivedEvent(eventLogId));

        // then
        verify(slackConversationPort, never()).sendMessage(anyLong(), anyString(), anyString(), anyString());
        verify(mockEventLog).markAsIgnored(anyString());
        verify(slackEventLogRepository).save(mockEventLog);
    }

    @Test
    @DisplayName("Integration 정보가 없으면 작업을 무시(IGNORED)하고 경고 로그를 남긴다.")
    void handleSlackEvent_IgnoredWhenIntegrationIsNull() {
        // given
        when(slackEventLogRepository.findByIdWithIntegrationAndWorkspace(eventLogId))
                .thenReturn(Optional.of(mockEventLog));
        when(mockEventLog.getIntegration()).thenReturn(null);

        // when
        slackEventHandler.handleSlackEvent(new SlackEventReceivedEvent(eventLogId));

        // then
        verify(slackConversationPort, never()).sendMessage(anyLong(), anyString(), anyString(), anyString());
        verify(mockEventLog).markAsIgnored(anyString());
        verify(slackEventLogRepository).save(mockEventLog);
    }
}
