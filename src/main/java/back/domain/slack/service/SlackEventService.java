// 경로: src/main/java/back/domain/slack/service/SlackEventService.java
package back.domain.slack.service;

import back.domain.slack.dto.request.SlackEventReq;

/**
 * Slack Events API에서 수신된 이벤트를 처리하고 중복을 검증하는 서비스입니다.
 */
public interface SlackEventService {

    /**
     * 수신된 Slack 이벤트를 데이터베이스에 기록하고 비즈니스 로직으로 라우팅합니다.
     * 중복된 이벤트(slackEventId)일 경우 처리를 무시합니다.
     *
     * @param request Slack으로부터 수신한 이벤트 페이로드 DTO
     */
    void processEvent(SlackEventReq request);
}