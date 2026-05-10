package back.domain.github.controller.docs;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.response.GithubCredentialInfoRes;
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
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Github Credential", description = "GitHub 자격 증명 관리 API")
public interface GithubCredentialControllerDocs {

    @Operation(summary = "GitHub 자격 증명(PAT) 신규 등록", description = "Workspace의 GitHub 자격 증명(Personal Access Token)을 신규 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "data": {
                                                "id": 1,
                                                "displayName": "Main-Repo-Access",
                                                "maskedToken": "ghp_****1234"
                                              },
                                              "message": "GitHub 자격 증명이 성공적으로 등록되었습니다."
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패 (필수 값 누락)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"자격 증명 이름(Display Name)은 필수입니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음 (관리자 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"워크스페이스 관리자 권한이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "데이터 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "요청 충돌",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"해당 이름의 GitHub 자격 증명이 이미 존재합니다.\"}")))
    })
    ResponseEntity<RsData<GithubCredentialInfoRes>> createGithubCredential(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "등록할 GitHub PAT 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "displayName": "Main-Repo-Access",
                                              "token": "ghp_realtoken1234567890"
                                            }
                                            """
                            )
                    )
            )
            @Valid GithubCredentialCreateReq req);
}