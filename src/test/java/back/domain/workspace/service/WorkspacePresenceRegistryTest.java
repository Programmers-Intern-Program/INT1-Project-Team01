package back.domain.workspace.service;

import back.domain.workspace.dto.WorkspacePresencePosition;
import back.domain.workspace.dto.response.WorkspacePresenceMember;
import back.domain.workspace.dto.response.WorkspacePresenceMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePresenceRegistryTest {

    private final WorkspacePresenceRegistry registry = new WorkspacePresenceRegistry();

    @Test
    @DisplayName("첫 SSE 연결 시 snapshot과 join 이벤트를 생성한다")
    void join_firstEmitter_returnsSnapshotAndJoinEvent() {
        SseEmitter emitter = emitter();

        WorkspacePresenceRegistry.JoinResult result = registry.join(1L, 10L, emitter);

        assertThat(result.snapshot().type()).isEqualTo("presence.snapshot");
        assertThat(result.snapshot().members()).extracting(WorkspacePresenceMember::memberId).containsExactly(10L);
        assertThat(result.joinEvent()).isNotNull();
        assertThat(registry.connectionsInWorkspace(1L))
                .extracting(WorkspacePresenceRegistry.WorkspacePresenceConnection::emitter)
                .containsExactly(emitter);
    }

    @Test
    @DisplayName("같은 회원의 추가 SSE 연결은 중복 join 이벤트를 만들지 않는다")
    void join_secondEmitterForSameMember_noJoinEvent() {
        registry.join(1L, 10L, emitter());

        WorkspacePresenceRegistry.JoinResult result = registry.join(1L, 10L, emitter());

        assertThat(result.joinEvent()).isNull();
        assertThat(result.snapshot().members()).extracting(WorkspacePresenceMember::memberId).containsExactly(10L);
    }

    @Test
    @DisplayName("마지막 SSE 연결이 닫힐 때만 leave 이벤트를 생성한다")
    void leave_lastEmitter_returnsLeaveEvent() {
        SseEmitter first = emitter();
        SseEmitter second = emitter();
        registry.join(1L, 10L, first);
        registry.join(1L, 10L, second);

        assertThat(registry.leave(1L, 10L, first).leaveEvent()).isNull();

        WorkspacePresenceRegistry.LeaveResult result = registry.leave(1L, 10L, second);
        assertThat(result.leaveEvent()).isNotNull();
        assertThat(result.leaveEvent().type()).isEqualTo("presence.leave");
    }

    @Test
    @DisplayName("위치 갱신은 snapshot에 마지막 위치와 상태를 반영한다")
    void updatePosition_reflectsSnapshot() {
        registry.join(1L, 10L, emitter());

        WorkspacePresenceMessage event = registry.updatePosition(
                1L, 10L, new WorkspacePresencePosition("49.2%", "27%"), "walking");
        WorkspacePresenceRegistry.JoinResult result = registry.join(1L, 20L, emitter());

        assertThat(event.type()).isEqualTo("presence.position");
        assertThat(result.snapshot().members())
                .filteredOn(member -> member.memberId() == 10L)
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.position().left()).isEqualTo("49.2%");
                    assertThat(member.position().top()).isEqualTo("27%");
                    assertThat(member.status()).isEqualTo("walking");
                });
    }

    @Test
    @DisplayName("온라인 상태가 아닌 회원의 위치 갱신은 이벤트를 만들지 않는다")
    void updatePosition_offlineMember_returnsNull() {
        WorkspacePresenceMessage event = registry.updatePosition(
                1L, 10L, new WorkspacePresencePosition("49.2%", "27%"), "walking");

        assertThat(event).isNull();
    }

    private SseEmitter emitter() {
        return new SseEmitter();
    }
}
