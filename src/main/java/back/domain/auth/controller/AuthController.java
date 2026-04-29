package back.domain.auth.controller;

import back.domain.auth.dto.request.GoogleLoginRequest;
import back.domain.auth.dto.request.LogoutAuthRequest;
import back.domain.auth.dto.request.RefreshAuthTokenRequest;
import back.domain.auth.dto.response.GoogleLoginResponse;
import back.domain.auth.dto.response.RefreshAuthTokenResponse;
import back.domain.auth.service.AuthService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@RequiredArgsConstructor
public class AuthController{
    private final AuthService authService;

    @PostMapping("/google/login")
    public ResponseEntity<RsData<GoogleLoginResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(new RsData<>(authService.loginWithGoogle(request.idToken()), "로그인 성공"));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<RsData<RefreshAuthTokenResponse>> refresh(
            @Valid @RequestBody RefreshAuthTokenRequest request) {
        return ResponseEntity.ok(new RsData<>(authService.refresh(request.refreshToken()), "토큰 재발급 성공"));
    }

    @PostMapping("/logout")
    public ResponseEntity<RsData<Void>> logout(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody LogoutAuthRequest request) {
        long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        authService.logout(memberId, request.refreshToken());
        return ResponseEntity.ok(new RsData<>("로그아웃 성공"));
    }

    private long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[AuthController#resolveAuthenticatedMemberId] authenticated member is missing",
                    CommonErrorCode.UNAUTHORIZED.defaultMessage());
        }

        return authenticatedMember.memberId();
    }
}
