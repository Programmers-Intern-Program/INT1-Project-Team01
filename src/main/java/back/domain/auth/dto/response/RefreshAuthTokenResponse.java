package back.domain.auth.dto.response;

public record RefreshAuthTokenResponse(String accessToken, String refreshToken) {}
