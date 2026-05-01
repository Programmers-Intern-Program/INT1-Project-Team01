package back.domain.workspace.repository;

import java.util.Optional;

import back.domain.workspace.entity.WorkspaceInvite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {
    boolean existsByToken(String token);

    Optional<WorkspaceInvite> findByToken(String token);
}
