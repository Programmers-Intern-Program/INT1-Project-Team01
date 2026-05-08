package back.domain.slack.client;

import back.domain.slack.dto.request.SlackMessageReq;

/**
 * Slack API 통신을 담당하는 클라이언트입니다.
 */
public interface SlackClient {

    /**
     * Slack 채널에 메시지를 전송합니다.
     *
     * @param botToken 복호화된 봇 토큰 (Bearer 인증용)
     * @param request  전송할 메시지 정보 (채널, 텍스트, 스레드 TS)
     */
    void sendMessage(String botToken, SlackMessageReq request);
}