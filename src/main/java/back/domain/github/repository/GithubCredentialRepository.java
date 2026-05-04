package back.domain.github.repository;

import back.domain.github.entity.GithubCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubCredentialRepository extends JpaRepository<GithubCredential, Long> {

    /**
     * 해당 워크스페이스 내에 동일한 식별 이름(displayName)을 가진 자격 증명이 존재하는지 확인합니다.
     */
    boolean existsByWorkspaceIdAndDisplayName(Long workspaceId, String displayName);
}