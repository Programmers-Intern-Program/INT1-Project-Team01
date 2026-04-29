package back.domain.auth.repository;

import back.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByMemberId(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken rt
            set rt.token = :newToken
            where rt.memberId = :memberId
              and rt.token = :currentToken
            """)
    int rotateIfMatch(
            @Param("memberId") Long memberId,
            @Param("currentToken") String currentToken,
            @Param("newToken") String newToken);

    void deleteByMemberId(Long memberId);
}
