package back.domain.auth.controller.docs;

import back.domain.auth.dto.request.GoogleLoginRequest;
import back.domain.auth.dto.request.LogoutAuthRequest;
import back.domain.auth.dto.request.RefreshAuthTokenRequest;
import back.domain.auth.dto.response.GoogleLoginResponse;
import back.domain.auth.dto.response.RefreshAuthTokenResponse;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "Google 로그인/토큰 재발급/로그아웃 API")
public interface AuthControllerDocs {

    @Operation(summary = "Google 로그인", description = "Google ID Token으로 로그인하고 내부 Access/Refresh 토큰을 발급합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    name = "성공",
                                    value =
                                            """
                    {
                      "data": {
                        "memberId": 101,
                        "name": "홍길동",
                        "email": "user101@example.com",
                        "role": "USER",
                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.access",
                        "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh"
                      },
                      "message": "로그인 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 구글 토큰",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "data": null,
                      "message": "유효하지 않은 구글 토큰입니다."
                    }
                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "data": null,
                      "message": "idToken-NotBlank-idToken은 필수입니다."
                    }
                    """)))
    })
    ResponseEntity<RsData<GoogleLoginResponse>> loginWithGoogle(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Google OAuth2 ID Token",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "idToken": "google-id-token-user-301"
                    }
                    """)))
            @Valid GoogleLoginRequest request);

    @Operation(summary = "Access/Refresh 토큰 재발급", description = "유효한 Refresh 토큰으로 Access/Refresh 토큰을 회전 재발급합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "data": {
                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.new-access",
                        "refreshToken": "eyJhbGciOiJIUzI1NiJ9.new-refresh"
                      },
                      "message": "토큰 재발급 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 리프레시 토큰",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "data": null,
                      "message": "유효하지 않은 토큰입니다."
                    }
                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "data": null,
                      "message": "refreshToken-NotBlank-refreshToken은 필수입니다."
                    }
                    """)))
    })
    ResponseEntity<RsData<RefreshAuthTokenResponse>> refresh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "재발급에 사용할 Refresh Token",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh"
                    }
                    """)))
            @Valid RefreshAuthTokenRequest request);

    @Operation(summary = "로그아웃", description = "인증된 사용자의 Refresh 토큰을 폐기합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"로그아웃 성공\"}"))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락 또는 토큰 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 소유가 아닌 리프레시 토큰",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"본인 토큰이 아닙니다.\"}")))
    })
    ResponseEntity<RsData<Void>> logout(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "폐기할 Refresh Token",
                    content = @Content(
                            mediaType = "application/json",
                            examples =
                            @ExampleObject(
                                    value =
                                            """
                    {
                      "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh"
                    }
                    """)))
            @Valid LogoutAuthRequest request);
}
