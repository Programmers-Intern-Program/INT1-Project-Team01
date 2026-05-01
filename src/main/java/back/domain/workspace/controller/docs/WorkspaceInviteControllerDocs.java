package back.domain.workspace.controller.docs;

import back.domain.workspace.dto.response.WorkspaceInvitePreviewRes;
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
import org.springframework.http.ResponseEntity;

@Tag(name = "WorkspaceInvite", description = "워크스페이스 초대 링크 조회/수락 API")
public interface WorkspaceInviteControllerDocs {

    @Operation(summary = "초대 링크 정보 조회", description = "초대 토큰으로 초대 정보를 조회합니다. 로그인 없이도 조회할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "초대 정보 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": {
                        "inviteId": 10,
                        "workspaceName": "개발팀 워크스페이스",
                        "role": "MEMBER",
                        "expiresAt": "2025-01-08T09:00:00",
                        "status": "PENDING",
                        "expired": false
                      },
                      "message": "초대 정보 조회 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "초대 링크를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"초대 링크가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<WorkspaceInvitePreviewRes>> getInviteInfo(
            @Parameter(description = "초대 토큰", example = "550e8400-e29b-41d4-a716-446655440000") String token);

    @Operation(summary = "초대 수락", description = "초대 토큰으로 워크스페이스 초대를 수락합니다. 만료되었거나 이미 사용된 초대 링크는 수락할 수 없습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "초대 수락 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"초대 수락 성공\"}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "만료되었거나 폐기된 초대 링크",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"만료된 초대 링크입니다.\"}"))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "초대 링크를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"초대 링크가 존재하지 않습니다.\"}"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용된 초대 링크 또는 이미 워크스페이스 멤버",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"이미 워크스페이스 멤버입니다.\"}")))
    })
    ResponseEntity<RsData<Void>> acceptInvite(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "초대 토큰", example = "550e8400-e29b-41d4-a716-446655440000") String token);
}
