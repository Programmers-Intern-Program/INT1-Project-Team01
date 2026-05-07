package back.testUtil;


import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 컨트롤러 슬라이스 테스트({@code @WebMvcTest})용 보안 설정.
 * <p>
 * CSRF 비활성화 및 모든 요청 허용으로 설정하여,
 * 보안 로직이 아닌 컨트롤러 로직 검증에 집중할 수 있도록 합니다.
 *
 * @author minhee
 * @since 2026-05-04
 */

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}