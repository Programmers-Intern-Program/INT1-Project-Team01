package back.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RestAccessDeniedHandlerTest {

    private RestAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RestAccessDeniedHandler(new ObjectMapper());
    }

    @Test
    @DisplayName("접근 거부 시 403 응답")
    void handle_accessDenied_returns403() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.handle(request, response, new AccessDeniedException("forbidden"));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("응답 Content-Type이 JSON")
    void handle_responseContentType_isJson() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.handle(request, response, new AccessDeniedException("forbidden"));

        // then
        assertThat(response.getContentType()).contains("application/json");
    }
}
