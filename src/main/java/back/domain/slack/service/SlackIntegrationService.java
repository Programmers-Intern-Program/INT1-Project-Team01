package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;

import java.util.List;

/**
 * Slack 외부 연동 정보에 대한 비즈니스 로직을 처리하는 서비스 인터페이스입니다.
 * <p>
 * Slack App 연결을 위한 봇 토큰 및 서명 비밀키의 등록, 조회, 수정, 삭제 등의 계약을 정의합니다.
 *
 * @author minhee
 * @since 2026-04-30
 */
public interface SlackIntegrationService {

    /**
     * Slack 채널과 Workspace 간의 연동 정보를 새롭게 등록합니다.
     */
    SlackIntegrationInfoRes createSlackIntegration(Long workspaceId, Long memberId, SlackIntegrationCreateReq req);

    /**
     * Workspace에 등록된 모든 Slack 연동 정보를 조회합니다.
     *
     * @param workspaceId 조회할 Workspace 식별자
     * @param memberId    요청하는 사용자(ADMIN) 식별자
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