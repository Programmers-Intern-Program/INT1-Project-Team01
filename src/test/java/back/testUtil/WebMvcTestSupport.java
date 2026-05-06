package back.testUtil;

import back.domain.slack.filter.SlackSignatureVerificationFilter;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.global.security.BearerTokenResolver;
import back.global.security.JwtTokenProvider;
import back.global.security.RestAccessDeniedHandler;
import back.global.security.RestAuthenticationEntryPoint;
import back.global.security.crypto.TinkCryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * WebMvcTest 에 대한 테스트 유틸입니다.
 * <p>
 * TestSecurityConfig를 임포트하여 보안 필터를 단순화하고,
 * JwtAuthenticationFilter 생성에 필요한 공통 빈들을 Mock으로 등록합니다.
 * 각 컨트롤러 전용 서비스 빈은 개별 테스트 클래스에서 선언합니다.
 *
 * @author minhee
 * @since 2026-05-04
 */
@Import(TestSecurityConfig.class)
public abstract class WebMvcTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @MockitoBean
    protected JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    protected BearerTokenResolver bearerTokenResolver;

    @MockitoBean
    protected RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @MockitoBean
    protected RestAccessDeniedHandler restAccessDeniedHandler;

    @MockitoBean
    protected SlackSignatureVerificationFilter slackSignatureVerificationFilter;

    @MockitoBean
    protected SlackIntegrationRepository slackIntegrationRepository;

    @MockitoBean
    protected TinkCryptoUtil tinkCryptoUtil;

    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            var request = invocation.getArgument(0);
            var response = invocation.getArgument(1);
            var chain = invocation.getArgument(2);
            ((jakarta.servlet.FilterChain) chain).doFilter(
                    (jakarta.servlet.ServletRequest) request,
                    (jakarta.servlet.ServletResponse) response
            );
            return null;
        }).when(slackSignatureVerificationFilter).doFilter(any(), any(), any());
    }
}