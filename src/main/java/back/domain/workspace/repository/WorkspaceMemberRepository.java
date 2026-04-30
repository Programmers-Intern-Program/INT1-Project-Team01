package back.domain.workspace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import back.domain.workspace.entity.WorkspaceMember;
import back.domain.workspace.enums.WorkspaceMemberRole;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    Optional<WorkspaceMember> findByWorkspaceIdAndMemberId(Long workspaceId, Long memberId);

    boolean existsByWorkspaceIdAndMemberId(Long workspaceId, Long memberId);

    @Query("SELECT wm FROM WorkspaceMember wm JOIN FETCH wm.workspace WHERE wm.member.id = :memberId")
    List<WorkspaceMember> findAllByMemberIdWithWorkspace(@Param("memberId") Long memberId);

    List<WorkspaceMember> findAllByWorkspaceId(Long workspaceId);

    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceMemberRole role);
}
