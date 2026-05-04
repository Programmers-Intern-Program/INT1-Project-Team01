package back.global.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 기능을 활성화하고 관련 설정을 담당하는 설정 클래스입니다.
 * <p>
 * {@link EnableJpaAuditing} 어노테이션을 통해 애플리케이션 내의 엔티티들이
 * 생성 시간(@CreatedDate)과 수정 시간(@LastModifiedDate)을 자동으로 기록할 수 있도록 지원합니다.
 * 메인 애플리케이션 클래스와 분리하여 구성함으로써, JPA 레이어에 의존하지 않는 테스트 환경을 보호합니다.
 *
 * <p><b>상속 정보:</b><br>
 * 별도의 상속 관계를 갖지 않으며, 스프링 프레임워크의 {@link Configuration} 인터페이스 원칙을 따릅니다.
 *
 * <p><b>주요 생성자:</b><br>
 * {@code JpaConfig()} <br>
 * 스프링 컨테이너에 의해 자동으로 호출되어 설정 빈을 등록합니다. <br>
 *
 * <p><b>빈 관리:</b><br>
 * 해당 클래스는 스프링 컨테이너에 의해 관리되는 설정(Configuration) 빈이며,
 * 내부적으로 JPA Auditing 처리를 위한 인프라 빈들을 등록합니다.
 *
 * <p><b>외부 모듈:</b><br>
 * Spring Data JPA 외부 라이브러리를 사용하며, {@code jakarta.persistence} 인터페이스와 연동됩니다.
 *
 * @author MintyU
 * @see org.springframework.data.jpa.repository.config.EnableJpaAuditing
 * @see org.springframework.data.jpa.domain.support.AuditingEntityListener
 * @since 2026-04-30
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}