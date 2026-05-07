package back.global.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing을 활성화하는 설정 클래스입니다.
 * 메인 애플리케이션과 분리하여 JPA 미사용 테스트 환경을 보호합니다.

 * @author minhee
 * @see org.springframework.data.jpa.repository.config.EnableJpaAuditing
 * @see org.springframework.data.jpa.domain.support.AuditingEntityListener
 * @since 2026-04-30
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}