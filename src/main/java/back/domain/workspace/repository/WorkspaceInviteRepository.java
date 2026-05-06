package back.domain.workspace.repository;

import java.util.List;
import java.util.Optional;

import back.domain.workspace.entity.WorkspaceInvite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {
    boolean existsByToken(String token);

    Optional<WorkspaceInvite> findByToken(String token);

    List<WorkspaceInvite> findAllByWorkspaceIdAndCreatedByMemberIdOrderByIdDesc(long workspaceId, long createdByMemberId);

    Optional<WorkspaceInvite> findByIdAndWorkspaceIdAndCreatedByMemberId(long inviteId, long workspaceId, long createdByMemberId);
}
