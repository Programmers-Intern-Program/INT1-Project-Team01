// 경로: src/main/java/back/domain/slack/service/SlackEventServiceImpl.java
package back.domain.slack.service;

import back.domain.slack.dto.request.SlackEventReq;
import back.domain.slack.entity.SlackEventLog;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.enums.SlackEventProcessingStatus;
import back.domain.slack.event.SlackEventReceivedEvent;
import back.domain.slack.repository.SlackEventLogRepository;
import back.domain.slack.repository.SlackIntegrationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "JsonMapper는 Jackson이 관리하는 스레드 안전 객체이며 불변으로 사용됨")
public class SlackEventServiceImpl implements SlackEventService {

    private final SlackEventLogRepository slackEventLogRepository;
    private final SlackIntegrationRepository slackIntegrationRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void processEvent(SlackEventReq request) {
        if (request.event() != null && "message".equals(request.event().type())) {
            log.debug(
                    "message 이벤트는 app_mention과 중복 수신되므로 skip합니다. event_id: {}", request.eventId());
            return;
        }

        if (request.eventId() == null) {
            log.debug(
                    "Slack 이벤트 ID가 누락되어 처리를 중단합니다. (Type: {})",
                    request.type());
            return;
        }

        if (slackEventLogRepository.existsBySlackEventId(request.eventId())) {
            log.info(
                    "이미 처리 중인 Slack 이벤트입니다. event_id: {}",
                    request.eventId());
            return;
        }

        String rawPayload = serializePayload(request);

        String teamId = request.teamId();
        String channelId = (request.event() != null) ? request.event().channel() : null;

        Optional<SlackIntegration> integrationOpt = (channelId != null)
                ? slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId(teamId, channelId)
                : Optional.empty();

        SlackEventLog.SlackEventLogBuilder logBuilder = SlackEventLog.builder()
                .slackEventId(request.eventId())
                .eventType(request.event() != null ? request.event().type() : request.type())
                .rawPayload(rawPayload);

        if (integrationOpt.isEmpty()) {
            SlackEventLog ignoredLog = logBuilder
                    .processingStatus(SlackEventProcessingStatus.IGNORED)
                    .error("매핑된 SlackIntegration 정보를 찾을 수 없습니다. (team: " + teamId + ", channel: " + channelId + ")")
                    .build();
            slackEventLogRepository.save(ignoredLog);
            log.info(
                    "미등록 채널 이벤트이므로 IGNORED 처리합니다. event_id: {}",
                    request.eventId());
            return;
        }

        SlackEventLog eventLog = logBuilder
                .integration(integrationOpt.get())
                .processingStatus(SlackEventProcessingStatus.RECEIVED)
                .build();

        slackEventLogRepository.save(eventLog);

        eventPublisher.publishEvent(new SlackEventReceivedEvent(eventLog.getId()));

        log.info("Slack 이벤트 수신 및 로그 저장 완료 (RECEIVED). event_id: {}", request.eventId());
    }

    private String serializePayload(SlackEventReq request) {
        try {
            return jsonMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Slack 페이로드 직렬화 중 오류 발생. event_id: {}", request.eventId(), e);
            return "";
        }
    }
}