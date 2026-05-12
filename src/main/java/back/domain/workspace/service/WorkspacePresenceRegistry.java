package back.domain.workspace.service;

import back.domain.workspace.dto.WorkspacePresencePosition;
import back.domain.workspace.dto.response.WorkspacePresenceMember;
import back.domain.workspace.dto.response.WorkspacePresenceMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "SseEmitter는 연결 자체를 나타내는 Spring 객체이며 broadcast 대상으로만 보관한다.")
public class WorkspacePresenceRegistry {
    private final Map<Long, WorkspacePresenceRoom> rooms = new HashMap<>();

    public synchronized JoinResult join(long workspaceId, long memberId, SseEmitter emitter) {
        WorkspacePresenceRoom room = rooms.computeIfAbsent(workspaceId, ignored -> new WorkspacePresenceRoom());
        WorkspacePresenceState state = room.members.computeIfAbsent(memberId, ignored -> new WorkspacePresenceState());

        boolean becameOnline = state.emitters.isEmpty();
        state.emitters.add(emitter);

        return new JoinResult(
                WorkspacePresenceMessage.snapshot(workspaceId, snapshotMembers(room)),
                becameOnline ? WorkspacePresenceMessage.join(workspaceId, memberId) : null);
    }

    public synchronized LeaveResult leave(long workspaceId, long memberId, SseEmitter emitter) {
        WorkspacePresenceRoom room = rooms.get(workspaceId);
        if (room == null) {
            return new LeaveResult(null);
        }

        WorkspacePresenceState state = room.members.get(memberId);
        if (state == null) {
            return new LeaveResult(null);
        }

        state.emitters.remove(emitter);
        if (!state.emitters.isEmpty()) {
            return new LeaveResult(null);
        }

        room.members.remove(memberId);
        if (room.members.isEmpty()) {
            rooms.remove(workspaceId);
        }

        return new LeaveResult(WorkspacePresenceMessage.leave(workspaceId, memberId));
    }

    public synchronized WorkspacePresenceMessage updatePosition(
            long workspaceId, long memberId, WorkspacePresencePosition position, String status) {
        WorkspacePresenceRoom room = rooms.get(workspaceId);
        if (room == null) {
            return null;
        }

        WorkspacePresenceState state = room.members.get(memberId);
        if (state == null) {
            return null;
        }

        state.position = position;
        state.status = status;

        return WorkspacePresenceMessage.position(workspaceId, memberId, position, status);
    }

    public synchronized List<WorkspacePresenceConnection> connectionsInWorkspace(long workspaceId) {
        WorkspacePresenceRoom room = rooms.get(workspaceId);
        if (room == null) {
            return List.of();
        }

        return room.members.entrySet().stream()
                .flatMap(entry -> entry.getValue().emitters.stream()
                        .map(emitter -> new WorkspacePresenceConnection(workspaceId, entry.getKey(), emitter)))
                .toList();
    }

    private List<WorkspacePresenceMember> snapshotMembers(WorkspacePresenceRoom room) {
        return room.members.entrySet().stream()
                .map(entry -> new WorkspacePresenceMember(
                        entry.getKey(), entry.getValue().position, entry.getValue().status))
                .sorted(Comparator.comparingLong(WorkspacePresenceMember::memberId))
                .toList();
    }

    public record JoinResult(WorkspacePresenceMessage snapshot, WorkspacePresenceMessage joinEvent) {}

    public record LeaveResult(WorkspacePresenceMessage leaveEvent) {}

    public record WorkspacePresenceConnection(long workspaceId, long memberId, SseEmitter emitter) {}

    private static class WorkspacePresenceRoom {
        private final Map<Long, WorkspacePresenceState> members = new HashMap<>();
    }

    private static class WorkspacePresenceState {
        private final Set<SseEmitter> emitters = new LinkedHashSet<>();
        private WorkspacePresencePosition position;
        private String status;
    }
}
