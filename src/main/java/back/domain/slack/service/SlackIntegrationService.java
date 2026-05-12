package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;

import java.util.List;

/**
 * Slack 외부 연동 정보에 대한 비즈니스 로직을 처리하는 서비스 인터페이스입니다.
 * <p>
 * Slack App 연결을 위한 봇 토큰 및 채널 정보의 등록, 조회, 수정, 삭제 등의 계약을 정의합니다.
 *
 * @author minhee
 * @since 2026-04-30
 */
public interface SlackIntegrationService {

    /**
     * Slack OAuth 2.0 연동을 시작하기 위한 설치 URL을 생성합니다.
     *
     * @param workspaceId 연동할 Workspace 식별자
     * @param memberId    요청하는 사용자(ADMIN) 식별자
     * @return Slack 인증 페이지 리다이렉트 URL
     */
    String getOAuthInstallUrl(Long workspaceId, Long memberId);

    /**
     * Slack OAuth 승인 완료 후 콜백을 처리하여 토큰을 발급받고 연동 정보를 DB에 저장합니다.
     *
     * @param code  Slack이 전달한 일회성 인가 코드
     * @param state 요청 시 전달했던 상태 값 (위변조 방지를 위해 workspaceId 등이 포함된 JWT 서명 토큰)
     */
    void handleOAuthCallback(String code, String state);

    /**
     * Slack 채널과 Workspace 간의 연동 정보를 새롭게 등록합니다.
     *
     * @param workspaceId Workspace 식별자
     * @param memberId    요청하는 사용자(ADMIN) 식별자
     * @param req         등록할 Slack 팀, 채널, 토큰 정보
     * @return 등록된 연동 정보
     * @throws back.global.exception.ServiceException 동일한 팀/채널 조합이 이미 존재하는 경우 (CONFLICT)
     */
    SlackIntegrationInfoRes createSlackIntegration(Long workspaceId, Long memberId, SlackIntegrationCreateReq req);

    /**
     * Workspace에 등록된 모든 Slack 연동 정보를 조회합니다.
     *
     * @param workspaceId 조회할 Workspace 식별자
     * @param memberId    요청하는 사용자(MEMBER) 식별자
     * @return 연동 정보 목록 (토큰 마스킹 처리됨)
     */
    List<SlackIntegrationInfoRes> getSlackIntegrations(Long workspaceId, Long memberId);

    /**
     * 등록된 Slack 연동 정보를 부분 수정(PATCH)합니다.
     * <p>
     * 전달된 DTO의 필드 중 null이거나 공백이 아닌 값만 기존 엔티티에 덮어씁니다.
     * 토큰 값이 전달되면 새롭게 암호화하여 저장합니다.
     *
     * @param workspaceId   Workspace 식별자
     * @param integrationId 수정할 연동 정보 식별자
     * @param memberId      요청하는 사용자(ADMIN) 식별자
     * @param req           수정할 필드를 담은 DTO
     * @return 수정된 연동 정보
     * @throws back.global.exception.ServiceException 변경하려는 팀/채널 조합이 이미 다른 연동 정보로 존재하는 경우 (CONFLICT)
     */
    SlackIntegrationInfoRes updateSlackIntegration(Long workspaceId, Long integrationId, Long memberId, SlackIntegrationUpdateReq req);

    /**
     * Slack 연동 정보를 삭제(Soft Delete)합니다.
     *
     * @param workspaceId   Workspace 식별자
     * @param integrationId 삭제할 연동 정보 식별자
     * @param memberId      요청하는 사용자(ADMIN) 식별자
     */
    void deleteSlackIntegration(Long workspaceId, Long integrationId, Long memberId);
}