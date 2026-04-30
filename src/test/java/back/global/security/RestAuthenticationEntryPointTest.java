package back.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RestAuthenticationEntryPointTest {

    private RestAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new RestAuthenticationEntryPoint(new ObjectMapper());
    }

    @Test
    @DisplayName("인증 실패 시 401 응답")
    void commence_unauthenticated_returns401() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(request, response, new InsufficientAuthenticationException("test"));

        // then
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("요청 속성의 커스텀 에러 메시지 사용")
    void commence_withCustomErrorAttribute_usesCustomMessage() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RestAuthenticationEntryPoint.ERROR_MESSAGE_ATTRIBUTE, "커스텀 에러 메시지");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(request, response, new InsufficientAuthenticationException("test"));

        // then
        assertThat(response.getContentAsString()).contains("커스텀 에러 메시지");
    }

    @Test
    @DisplayName("응답 Content-Type이 JSON")
    void commence_responseContentType_isJson() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(request, response, new InsufficientAuthenticationException("test"));

        // then
        assertThat(response.getContentType()).contains("application/json");
    }
}
