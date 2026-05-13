package back.domain.member.controller.docs;

import back.domain.member.dto.request.MemberProfileUpdateReq;
import back.domain.member.dto.response.MemberProfileRes;
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

@Tag(name = "Member", description = "회원 프로필 API")
public interface MemberControllerDocs {

    @Operation(
            summary = "내 멤버 프로필 조회",
            description = "오피스에서 표시되는 내 프로필과 아바타 설정을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "data": {
                        "memberId": 1,
                        "displayName": "홍길동",
                        "avatarKind": "mira",
                        "avatarColors": {
                          "skin": "#f8d4b0",
                          "hair": "#1a1a1a",
                          "shirt": "#2a3a4a"
                        }
                      },
                      "message": "멤버 프로필 조회 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}")))
    })
    ResponseEntity<RsData<MemberProfileRes>> getMyProfile(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(
            summary = "내 멤버 프로필 수정",
            description = "오피스에서 표시되는 내 이름과 아바타 설정을 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "data": {
                        "memberId": 1,
                        "displayName": "길동",
                        "avatarKind": "mira",
                        "avatarColors": {
                          "skin": "#f8d4b0",
                          "hair": "#1a1a1a",
                          "shirt": "#2a3a4a"
                        }
                      },
                      "message": "멤버 프로필 수정 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "data": null,
                      "message": "아바타 색상은 #RRGGBB 형식이어야 합니다."
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}")))
    })
    ResponseEntity<RsData<MemberProfileRes>> updateMyProfile(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "멤버 프로필 수정 요청",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "displayName": "길동",
                      "avatarKind": "mira",
                      "avatarColors": {
                        "skin": "#f8d4b0",
                        "hair": "#1a1a1a",
                        "shirt": "#2a3a4a"
                      }
                    }
                    """)))
            @Valid MemberProfileUpdateReq request);
}
