package back.domain.workspace.service;

import back.domain.workspace.dto.request.WorkspacePresencePositionUpdateReq;
import back.domain.workspace.dto.response.WorkspacePresenceMessage;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.AuthenticatedMember;
import back.global.security.BearerTokenResolver;
import back.global.security.JwtTokenProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
public class WorkspacePresenceServiceImpl implements WorkspacePresenceService {
    private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private final WorkspacePresenceRegistry registry;
    private final JwtTokenProvider jwtTokenProvider;
    private final BearerTokenResolver bearerTokenResolver;
    private final WorkspaceAccessValidator workspaceAccessValidator;

    @Override
    public SseEmitter stream(long workspaceId, String authorizationHeader, String token, String accessToken) {
        long memberId = resolveMemberId(workspaceId, authorizationHeader, token, accessToken);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        emitter.onCompletion(() -> disconnect(workspaceId, memberId, emitter));
        emitter.onTimeout(() -> disconnect(workspaceId, memberId, emitter));
        emitter.onError(error -> disconnect(workspaceId, memberId, emitter));

        WorkspacePresenceRegistry.JoinResult result = registry.join(workspaceId, memberId, emitter);
        try {
            send(emitter, result.snapshot());
            if (result.joinEvent() != null) {
                broadcast(workspaceId, result.joinEvent(), List.of(emitter));
            }
        } catch (IOException | IllegalStateException exception) {
            disconnect(workspaceId, memberId, emitter);
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[WorkspacePresenceServiceImpl#stream] failed to open SSE stream",
                    "Presence 연결에 실패했습니다.");
        }

        return emitter;
    }

    @Override
    public void updateMyPosition(
            long workspaceId, AuthenticatedMember authenticatedMember, WorkspacePresencePositionUpdateReq request) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        workspaceAccessValidator.requireMember(workspaceId, memberId);

        WorkspacePresenceMessage positionEvent =
                registry.updatePosition(workspaceId, memberId, request.position(), request.status());
        if (positionEvent != null) {
            broadcast(workspaceId, positionEvent, List.of());
        }
    }

    private long resolveMemberId(long workspaceId, String authorizationHeader, String token, String accessToken) {
        String resolvedToken = resolveAccessToken(authorizationHeader, token, accessToken);
        long memberId = jwtTokenProvider.getMemberIdFromAccessToken(resolvedToken);
        workspaceAccessValidator.requireMember(workspaceId, memberId);
        return memberId;
    }

    private String resolveAccessToken(String authorizationHeader, String token, String accessToken) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return bearerTokenResolver.resolve(authorizationHeader);
        }
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken.trim();
        }

        throw new ServiceException(
                CommonErrorCode.UNAUTHORIZED,
                "[WorkspacePresenceServiceImpl#resolveAccessToken] access token is missing",
                "로그인이 필요합니다.");
    }

    private long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[WorkspacePresenceServiceImpl#resolveAuthenticatedMemberId] authenticated member is null",
                    "로그인이 필요합니다.");
        }

        return authenticatedMember.memberId();
    }

    private void disconnect(long workspaceId, long memberId, SseEmitter emitter) {
        WorkspacePresenceRegistry.LeaveResult result = registry.leave(workspaceId, memberId, emitter);
        if (result.leaveEvent() != null) {
            broadcast(workspaceId, result.leaveEvent(), List.of(emitter));
        }
    }

    private void broadcast(long workspaceId, WorkspacePresenceMessage message, List<SseEmitter> excludedEmitters) {
        for (WorkspacePresenceRegistry.WorkspacePresenceConnection connection :
                registry.connectionsInWorkspace(workspaceId)) {
            if (excludedEmitters.contains(connection.emitter())) {
                continue;
            }

            try {
                send(connection.emitter(), message);
            } catch (IOException | IllegalStateException exception) {
                disconnect(connection.workspaceId(), connection.memberId(), connection.emitter());
            }
        }
    }

    private void send(SseEmitter emitter, WorkspacePresenceMessage message) throws IOException {
        emitter.send(SseEmitter.event()
                .reconnectTime(3_000L)
                .data(message, MediaType.APPLICATION_JSON));
    }
}
