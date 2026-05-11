package back.domain.slack.client;

import back.domain.slack.dto.request.SlackMessageReq;
import back.domain.slack.dto.response.SlackOAuthAccessRes;

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

    /**
     * Slack OAuth Code를 Access Token으로 교환합니다.
     *
     * @param code         Slack 권한 승인 후 리다이렉트된 코드
     * @param clientId     Slack App Client ID
     * @param clientSecret Slack App Client Secret
     * @param redirectUri  요청 시 사용했던 Redirect URI (선택적)
     * @return Slack으로부터 발급받은 토큰 및 워크스페이스 정보
     */
    SlackOAuthAccessRes exchangeToken(String code, String clientId, String clientSecret, String redirectUri);
}