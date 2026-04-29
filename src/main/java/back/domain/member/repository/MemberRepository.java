package back.domain.member.repository;

import back.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByGoogleSub(String googleSub);

    Optional<Member> findByEmail(String email);
}
