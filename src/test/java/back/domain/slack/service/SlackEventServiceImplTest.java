package back.domain.slack.service;

import back.domain.slack.dto.request.SlackEventReq;
import back.domain.slack.entity.SlackEventLog;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.enums.SlackEventProcessingStatus;
import back.domain.slack.event.SlackEventReceivedEvent;
import back.domain.slack.repository.SlackEventLogRepository;
import back.domain.slack.repository.SlackIntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SlackEventServiceImpl} 단위 테스트.
 * <p>
 * 멱등성 처리, 이벤트 로그 저장, 스프링 이벤트 발행 등 핵심 비즈니스 로직을 검증합니다.
 *
 * @author minhee
 * @since 2026-05-04
 */
@ExtendWith(MockitoExtension.class)
class SlackEventServiceImplTest {

    @Mock
    private SlackEventLogRepository slackEventLogRepository;

    @Mock
    private SlackIntegrationRepository slackIntegrationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SlackEventServiceImpl slackEventService;

    @BeforeEach
    void setUp() {
        slackEventService = new SlackEventServiceImpl(
                slackEventLogRepository,
                slackIntegrationRepository,
                JsonMapper.builder().build(),
                eventPublisher
        );
    }

    private SlackEventReq buildRequest(String eventId, String teamId, String channelId) {
        SlackEventReq.SlackEventDetail event = new SlackEventReq.SlackEventDetail(
                "app_mention", channelId, null, "안녕!", null);
        return new SlackEventReq("event_callback", null, teamId, eventId, event);
    }

    @Test
    @DisplayName("event_id가 null이면 처리를 중단한다")
    void nullEventId_stopsProcessing() {
        // given
        SlackEventReq request = new SlackEventReq("event_callback", null, "T12345", null, null);

        // when
        slackEventService.processEvent(request);

        // then
        verify(slackEventLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 처리된 event_id면 중복 처리하지 않는다 (멱등성)")
    void duplicateEventId_stopsProcessing() {
        // given
        SlackEventReq request = buildRequest("Ev001", "T12345", "C12345");
        given(slackEventLogRepository.existsBySlackEventId("Ev001")).willReturn(true);

        // when
        slackEventService.processEvent(request);

        // then
        verify(slackEventLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("등록되지 않은 채널이면 IGNORED 상태로 저장한다")
    void unregisteredChannel_savesIgnoredLog() {
        // given
        SlackEventReq request = buildRequest("Ev001", "T12345", "C_UNKNOWN");
        given(slackEventLogRepository.existsBySlackEventId("Ev001")).willReturn(false);
        given(slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId("T12345", "C_UNKNOWN"))
                .willReturn(Optional.empty());

        // when
        slackEventService.processEvent(request);

        // then
        ArgumentCaptor<SlackEventLog> captor = ArgumentCaptor.forClass(SlackEventLog.class);
        verify(slackEventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo(SlackEventProcessingStatus.IGNORED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("정상 이벤트면 RECEIVED 상태로 저장하고 이벤트를 발행한다")
    void validEvent_savesReceivedLogAndPublishesEvent() {
        // given
        SlackEventReq request = buildRequest("Ev001", "T12345", "C12345");
        SlackIntegration integration = SlackIntegration.builder().build();

        given(slackEventLogRepository.existsBySlackEventId("Ev001")).willReturn(false);
        given(slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId("T12345", "C12345"))
                .willReturn(Optional.of(integration));

        SlackEventLog savedLog = SlackEventLog.builder()
                .slackEventId("Ev001")
                .processingStatus(SlackEventProcessingStatus.RECEIVED)
                .build();
        given(slackEventLogRepository.save(any())).willReturn(savedLog);

        // when
        slackEventService.processEvent(request);

        // then
        ArgumentCaptor<SlackEventLog> logCaptor = ArgumentCaptor.forClass(SlackEventLog.class);
        verify(slackEventLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessingStatus()).isEqualTo(SlackEventProcessingStatus.RECEIVED);

        verify(eventPublisher).publishEvent(any(SlackEventReceivedEvent.class));
    }
}