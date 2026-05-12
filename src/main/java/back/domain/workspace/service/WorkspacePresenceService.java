package back.domain.workspace.service;

import back.domain.workspace.dto.request.WorkspacePresencePositionUpdateReq;
import back.global.security.AuthenticatedMember;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 워크스페이스 Presence (SSE 기반 실시간 접속/위치 동기화)를 처리합니다.
 */
public interface WorkspacePresenceService {

    /**
     * 워크스페이스 Presence SSE 스트림을 시작합니다.
     *
     * @param workspaceId         대상 워크스페이스 ID
     * @param authorizationHeader Authorization 헤더 (Bearer 토큰)
     * @param token               query string으로 전달된 액세스 토큰 (EventSource 대체용)
     * @param accessToken         query string으로 전달된 액세스 토큰 (호환용)
     * @return SSE 이벤트 emitter
     * @throws back.global.exception.ServiceException 액세스 토큰이 없거나(UNAUTHORIZED), 워크스페이스 멤버가 아닌 경우(FORBIDDEN)
     * @throws back.global.exception.ServiceException SSE 스트림 초기화에 실패한 경우 (INTERNAL_SERVER_ERROR)
     */
    SseEmitter stream(long workspaceId, String authorizationHeader, String token, String accessToken);

    /**
     * 요청자의 현재 위치/상태를 갱신하고 같은 워크스페이스의 다른 구독자에게 브로드캐스트합니다.
     *
     * @param workspaceId         대상 워크스페이스 ID
     * @param authenticatedMember 인증된 회원 정보
     * @param request             위치/상태 갱신 요청 DTO
     * @throws back.global.exception.ServiceException 인증되지 않은 경우 (UNAUTHORIZED)
     * @throws back.global.exception.ServiceException 워크스페이스 멤버가 아닌 경우 (FORBIDDEN)
     */
    void updateMyPosition(
            long workspaceId,
            AuthenticatedMember authenticatedMember,
            WorkspacePresencePositionUpdateReq request);
}
