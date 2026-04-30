package back.global.security;

import back.global.exception.ServiceException;
import back.global.security.TokenAuthenticationException.TokenErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String INVALID_TOKEN_MESSAGE = "유효하지 않은 토큰입니다.";
    private static final String REFRESH_TOKEN_ENDPOINT = "/api/v1/auth/token/refresh";
    private static final RequestMatcher REFRESH_TOKEN_REQUEST_MATCHER = request -> {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }

        String pathWithinApplication = resolvePathWithinApplication(request);
        return REFRESH_TOKEN_ENDPOINT.equals(pathWithinApplication);
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final BearerTokenResolver bearerTokenResolver;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return REFRESH_TOKEN_REQUEST_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String accessToken = bearerTokenResolver.resolve(authorizationHeader);
            JwtTokenProvider.AccessTokenPayload payload = jwtTokenProvider.getAccessTokenPayload(accessToken);

            AuthenticatedMember authenticatedMember = new AuthenticatedMember(payload.memberId(), payload.role());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedMember, null, List.of(new SimpleGrantedAuthority("ROLE_" + payload.role())));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ServiceException exception) {
            SecurityContextHolder.clearContext();
            String errorMessage = normalizeErrorMessage(exception);
            request.setAttribute(RestAuthenticationEntryPoint.ERROR_MESSAGE_ATTRIBUTE, errorMessage);
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException(errorMessage, exception));
        }
    }

    private String normalizeErrorMessage(ServiceException exception) {
        if (exception instanceof TokenAuthenticationException tokenException) {
            if (tokenException.tokenErrorType() == TokenErrorType.EXPIRED) {
                return tokenException.getClientMessage();
            }
        }

        return INVALID_TOKEN_MESSAGE;
    }

    private static String resolvePathWithinApplication(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }

        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return "";
        }

        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isBlank()) {
            return requestUri;
        }

        if (requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }

        return requestUri;
    }
}
