package back.global.security;

import back.global.exception.CommonErrorCode;
import back.global.response.RsData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    public static final String ERROR_MESSAGE_ATTRIBUTE = "AUTH_ERROR_MESSAGE";

    private static final String DEFAULT_ERROR_MESSAGE = CommonErrorCode.UNAUTHORIZED.defaultMessage();

    private final ObjectWriter objectWriter;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectWriter = objectMapper.writer();
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String errorMessage = readStringAttribute(request, ERROR_MESSAGE_ATTRIBUTE, DEFAULT_ERROR_MESSAGE);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectWriter.writeValue(response.getWriter(), new RsData<>(errorMessage));
    }

    private String readStringAttribute(HttpServletRequest request, String name, String defaultValue) {
        Object value = request.getAttribute(name);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return defaultValue;
    }
}
