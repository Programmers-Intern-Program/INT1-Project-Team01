package back.domain.auth.dto.response;

public record GoogleLoginResponse(
        long memberId,
        String name,
        String email,
        String role,
        String accessToken,
        String refreshToken) {}
