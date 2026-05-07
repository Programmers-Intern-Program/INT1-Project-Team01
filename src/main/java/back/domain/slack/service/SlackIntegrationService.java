package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;

/**
 * Slack 외부 연동 정보에 대한 비즈니스 로직을 처리하는 서비스 인터페이스입니다.
 * <p>
 * Slack App 연결을 위한 봇 토큰 및 서명 비밀키의 등록, 조회, 검증 등의 계약을 정의합니다.
 *
 * @author minhee
 * @since 2026-04-30
 */
public interface SlackIntegrationService {

    /**
     * Slack 채널과 Workspace 간의 연동 정보를 새롭게 등록합니다.
     * <p>
     * 등록 전 해당 Slack 팀과 채널이 이미 연동되어 있는지 중복 검증을 수행합니다.
     * 전달받은 평문 토큰은 데이터베이스 저장 과정에서 JPA Converter에 의해 암호화됩니다.
     *
     * @param workspaceId 연동할 Workspace의 식별자
     * @param memberId    등록을 요청하는 사용자(ADMIN 권한 보유자)의 식별자
     * @param req         등록할 Slack 연동 정보 (Team ID, Channel ID, Token 등)
     * @return 등록 완료된 연동 정보 (민감 정보는 마스킹 처리됨)
     */
    SlackIntegrationInfoRes createSlackIntegration(Long workspaceId, Long memberId, SlackIntegrationCreateReq req);
}