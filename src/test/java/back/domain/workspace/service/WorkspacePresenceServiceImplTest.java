package back.domain.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import back.domain.workspace.dto.WorkspacePresencePosition;
import back.domain.workspace.dto.request.WorkspacePresencePositionUpdateReq;
import back.domain.workspace.dto.response.WorkspacePresenceMessage;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.AuthenticatedMember;
import back.global.security.BearerTokenResolver;
import back.global.security.JwtTokenProvider;

class WorkspacePresenceServiceImplTest {

    private WorkspacePresenceRegistry registry;
    private JwtTokenProvider jwtTokenProvider;
    private BearerTokenResolver bearerTokenResolver;
    private WorkspaceAccessValidator workspaceAccessValidator;
    private WorkspacePresenceServiceImpl service;

    @BeforeEach
    void setUp() {
        registry = mock(WorkspacePresenceRegistry.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        bearerTokenResolver = mock(BearerTokenResolver.class);
        workspaceAccessValidator = mock(WorkspaceAccessValidator.class);
        service = new WorkspacePresenceServiceImpl(
                registry, jwtTokenProvider, bearerTokenResolver, workspaceAccessValidator);
    }

    @Test
    @DisplayName("Authorization 헤더 기반 SSE 스트림을 정상적으로 생성한다")
    void stream_withBearerHeader_returnsEmitter() {
        long workspaceId = 1L;
        long memberId = 100L;
        WorkspacePresenceMessage snapshot = WorkspacePresenceMessage.snapshot(workspaceId, List.of());
        WorkspacePresenceMessage joinEvent = WorkspacePresenceMessage.join(workspaceId, memberId);

        given(bearerTokenResolver.resolve("Bearer abc")).willReturn("abc");
        given(jwtTokenProvider.getMemberIdFromAccessToken("abc")).willReturn(memberId);
        given(registry.join(eq(workspaceId), eq(memberId), any(SseEmitter.class)))
                .willReturn(new WorkspacePresenceRegistry.JoinResult(snapshot, joinEvent));
        given(registry.connectionsInWorkspace(workspaceId)).willReturn(List.of());

        SseEmitter emitter = service.stream(workspaceId, "Bearer abc", null, null);

        assertThat(emitter).isNotNull();
        verify(workspaceAccessValidator).requireMember(workspaceId, memberId);
        verify(registry).join(eq(workspaceId), eq(memberId), any(SseEmitter.class));
    }

    @Test
    @DisplayName("token query string으로도 SSE 스트림을 생성할 수 있다")
    void stream_withTokenParam_returnsEmitter() {
        long workspaceId = 2L;
        long memberId = 200L;

        given(jwtTokenProvider.getMemberIdFromAccessToken("plain-token")).willReturn(memberId);
        given(registry.join(eq(workspaceId), eq(memberId), any(SseEmitter.class)))
                .willReturn(new WorkspacePresenceRegistry.JoinResult(
                        WorkspacePresenceMessage.snapshot(workspaceId, List.of()), null));
        given(registry.connectionsInWorkspace(workspaceId)).willReturn(List.of());

        SseEmitter emitter = service.stream(workspaceId, null, "plain-token", null);

        assertThat(emitter).isNotNull();
        verify(workspaceAccessValidator).requireMember(workspaceId, memberId);
    }

    @Test
    @DisplayName("accessToken query string으로도 SSE 스트림을 생성할 수 있다")
    void stream_withAccessTokenParam_returnsEmitter() {
        long workspaceId = 3L;
        long memberId = 300L;

        given(jwtTokenProvider.getMemberIdFromAccessToken("legacy-token")).willReturn(memberId);
        given(registry.join(eq(workspaceId), eq(memberId), any(SseEmitter.class)))
                .willReturn(new WorkspacePresenceRegistry.JoinResult(
                        WorkspacePresenceMessage.snapshot(workspaceId, List.of()), null));
        given(registry.connectionsInWorkspace(workspaceId)).willReturn(List.of());

        SseEmitter emitter = service.stream(workspaceId, null, null, "legacy-token");

        assertThat(emitter).isNotNull();
        verify(jwtTokenProvider).getMemberIdFromAccessToken("legacy-token");
    }

    @Test
    @DisplayName("토큰이 없으면 UNAUTHORIZED 예외를 던진다")
    void stream_missingToken_throwsUnauthorized() {
        assertThatThrownBy(() -> service.stream(1L, null, "  ", ""))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("join 이벤트가 있으면 같은 워크스페이스의 다른 emitter에게 브로드캐스트한다")
    void stream_broadcastsJoinEventToOtherConnections() throws Exception {
        long workspaceId = 4L;
        long memberId = 400L;
        SseEmitter otherEmitter = mock(SseEmitter.class);
        WorkspacePresenceMessage joinEvent = WorkspacePresenceMessage.join(workspaceId, memberId);

        given(bearerTokenResolver.resolve("Bearer t")).willReturn("t");
        given(jwtTokenProvider.getMemberIdFromAccessToken("t")).willReturn(memberId);
        given(registry.join(eq(workspaceId), eq(memberId), any(SseEmitter.class)))
                .willReturn(new WorkspacePresenceRegistry.JoinResult(
                        WorkspacePresenceMessage.snapshot(workspaceId, List.of()), joinEvent));
        given(registry.connectionsInWorkspace(workspaceId)).willReturn(List.of(
                new WorkspacePresenceRegistry.WorkspacePresenceConnection(workspaceId, 999L, otherEmitter)));

        service.stream(workspaceId, "Bearer t", null, null);

        verify(otherEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("위치 업데이트가 성공하면 같은 워크스페이스 emitter들에게 브로드캐스트한다")
    void updateMyPosition_success_broadcasts() throws Exception {
        long workspaceId = 5L;
        long memberId = 500L;
        SseEmitter subscriber = mock(SseEmitter.class);
        WorkspacePresencePosition position = new WorkspacePresencePosition("10px", "20px");
        WorkspacePresencePositionUpdateReq request = new WorkspacePresencePositionUpdateReq(position, "ACTIVE");

        given(registry.updatePosition(workspaceId, memberId, position, "ACTIVE"))
                .willReturn(WorkspacePresenceMessage.position(workspaceId, memberId, position, "ACTIVE"));
        given(registry.connectionsInWorkspace(workspaceId)).willReturn(List.of(
                new WorkspacePresenceRegistry.WorkspacePresenceConnection(workspaceId, memberId, subscriber)));

        service.updateMyPosition(workspaceId, new AuthenticatedMember(memberId, "USER"), request);

        verify(workspaceAccessValidator).requireMember(workspaceId, memberId);
        verify(subscriber, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("registry가 null 이벤트를 반환하면 브로드캐스트하지 않는다")
    void updateMyPosition_nullEvent_doesNotBroadcast() {
        long workspaceId = 6L;
        long memberId = 600L;
        WorkspacePresencePosition position = new WorkspacePresencePosition("0", "0");
        WorkspacePresencePositionUpdateReq request = new WorkspacePresencePositionUpdateReq(position, null);

        given(registry.updatePosition(workspaceId, memberId, position, null)).willReturn(null);

        service.updateMyPosition(workspaceId, new AuthenticatedMember(memberId, "USER"), request);

        verify(registry, never()).connectionsInWorkspace(anyLong());
    }

    @Test
    @DisplayName("인증되지 않은 사용자가 위치 업데이트를 요청하면 UNAUTHORIZED 예외를 던진다")
    void updateMyPosition_authenticatedMemberNull_throwsUnauthorized() {
        WorkspacePresencePositionUpdateReq request =
                new WorkspacePresencePositionUpdateReq(new WorkspacePresencePosition("0", "0"), null);

        assertThatThrownBy(() -> service.updateMyPosition(1L, null, request))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }
}
