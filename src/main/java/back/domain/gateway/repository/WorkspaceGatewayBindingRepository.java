package back.domain.gateway.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import back.domain.gateway.entity.WorkspaceGatewayBinding;

public interface WorkspaceGatewayBindingRepository extends JpaRepository<WorkspaceGatewayBinding, Long> {

    Optional<WorkspaceGatewayBinding> findByWorkspaceId(Long workspaceId);
}
