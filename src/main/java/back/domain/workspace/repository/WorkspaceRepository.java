package back.domain.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.workspace.entity.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

}
