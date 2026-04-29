package back.domain.auth.service;

import back.domain.auth.dto.response.GoogleLoginResponse;
import back.domain.auth.dto.response.RefreshAuthTokenResponse;

public interface AuthService {
    GoogleLoginResponse loginWithGoogle(String idToken);

    RefreshAuthTokenResponse refresh(String refreshToken);

    void logout(long authenticatedMemberId, String refreshToken);
}
