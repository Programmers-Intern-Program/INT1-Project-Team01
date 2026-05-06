package back.domain.slack.service;

import back.domain.slack.dto.request.SlackEventReq;
import back.domain.slack.entity.SlackEventLog;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.enums.SlackEventProcessingStatus;
import back.domain.slack.event.SlackEventReceivedEvent;
import back.domain.slack.repository.SlackEventLogRepository;
import back.domain.slack.repository.SlackIntegrationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Value("${security.slack.store-raw-payload:true}")
    private boolean storeRawPayload;

    @Override
    @Transactional
    public void processEvent(SlackEventReq request) {
        if (request.eventId() == null) {
            log.debug(
                    "Slack 이벤트 ID가 누락되어 처리를 중단합니다. (Type: {})",
                    request.type());
            return;
        }

        String rawPayload = storeRawPayload ? serializePayload(request) : null;

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

        boolean isSaved = trySaveEventLog(eventLog, request.eventId());
        if (!isSaved) return;

        eventPublisher.publishEvent(new SlackEventReceivedEvent(eventLog.getId()));
        log.info(
                "Slack 이벤트 수신 및 로그 저장 완료 (RECEIVED). event_id: {}",
                request.eventId());
    }

    /**
     * 예외를 활용해 Slack 재전송으로 인한 Race Condition(동시성) 문제를 안전하게 방어합니다.
     * DB의 유니크 제약조건(uk_slack_event_id)에 의존하여 중복 저장을 차단합니다.
     *
     * @param logEntity 저장할 SlackEventLog 엔티티
     * @param eventId   중복 로깅을 위한 Slack 원본 이벤트 식별자
     * @return 저장 성공 시 true, 동시성 충돌로 인한 저장 실패 시 false 반환
     */
    private boolean trySaveEventLog(SlackEventLog logEntity, String eventId) {
        try {
            slackEventLogRepository.save(logEntity);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("중복 이벤트 감지 (동시성 방어). event_id: {}", eventId);
            return false;
        }
    }

    /**
     * 수신된 SlackEventReq 객체를 JSON 문자열로 직렬화합니다.
     * 예외 발생 시 시스템 오류로 전파하지 않고 빈 문자열을 반환하여 주 로직(이벤트 수신)에 영향을 주지 않도록 합니다.
     *
     * @param request 파싱된 Slack 이벤트 요청 DTO
     * @return 직렬화된 JSON 형식의 문자열 (실패 시 빈 문자열 "")
     */
    private String serializePayload(SlackEventReq request) {
        try {
            return jsonMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Slack 페이로드 직렬화 중 오류 발생. event_id: {}", request.eventId(), e);
            return "";
        }
    }
}