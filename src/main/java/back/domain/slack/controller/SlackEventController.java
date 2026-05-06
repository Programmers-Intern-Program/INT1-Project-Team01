package back.domain.slack.controller;

import back.domain.slack.dto.request.SlackEventReq;
import back.domain.slack.service.SlackEventService;
import back.global.response.RsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Slack Events API로부터 수신되는 Webhook 요청을 처리하는 컨트롤러입니다.
 */

@Slf4j
@RestController
@RequestMapping("/api/v1/slack")
@RequiredArgsConstructor
public class SlackEventController {

    private final SlackEventService slackEventService;
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of("app_mention");

    /**
     * Slack 이벤트를 수신하여 유형별로 처리합니다.
     * 1. url_verification: 슬랙 앱 설정 시 URL 유효성 검증
     * 2. event_callback: 실제 비즈니스 이벤트 (app_mention 등)
     */
    @PostMapping("/events")
    public ResponseEntity<Object> handleSlackEvent(@RequestBody SlackEventReq request) {
        log.debug("Slack 이벤트 수신 - type: {}, event_id: {}", request.type(), request.eventId());

        if ("url_verification".equals(request.type())) {
            log.info("Slack URL Verification 시도 수신");
            return ResponseEntity.ok(Map.of("challenge", request.challenge()));
        }

        if (request.event() == null || !ALLOWED_EVENT_TYPES.contains(request.event().type())) {
            log.debug("허용되지 않은 이벤트 타입 skip. type: {}",
                    request.event() != null ? request.event().type() : "null");
            return ResponseEntity.ok(new RsData<>(null, "성공"));
        }

        slackEventService.processEvent(request);
        return ResponseEntity.ok(new RsData<>(null, "성공"));
    }
}