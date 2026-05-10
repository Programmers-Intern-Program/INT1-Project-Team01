package back.domain.github.controller.docs;

import back.domain.github.dto.request.GithubCredentialCreateReq;
import back.domain.github.dto.request.GithubCredentialUpdateReq;
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

import java.util.List;

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

    @Operation(summary = "GitHub 자격 증명 목록 조회", description = "해당 Workspace에 등록된 모든 GitHub 자격 증명 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "data": [
                                                {
                                                  "id": 1,
                                                  "displayName": "Main-Repo-Access",
                                                  "maskedToken": "ghp_****1234"
                                                }
                                              ],
                                              "message": "GitHub 자격 증명 목록을 성공적으로 조회했습니다."
                                            }
                                            """)))
    })
    @ApiResponse(
            responseCode = "403",
            description = "접근 권한 없음 (워크스페이스 멤버 아님)",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"data\":null,\"message\":\"워크스페이스 접근 권한이 없습니다.\"}")))
    ResponseEntity<RsData<List<GithubCredentialInfoRes>>> getGithubCredentials(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(summary = "GitHub 자격 증명 부분 수정", description = "등록된 GitHub 자격 증명을 부분 수정합니다. 값이 있는 필드만 업데이트됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "data": {
                                                "id": 1,
                                                "displayName": "Updated-Repo-Access",
                                                "maskedToken": "ghp_****5678"
                                              },
                                              "message": "GitHub 자격 증명이 성공적으로 수정되었습니다."
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "자격 증명 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"해당 GitHub 자격 증명을 찾을 수 없습니다.\"}")))
    })
    ResponseEntity<RsData<GithubCredentialInfoRes>> updateGithubCredential(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(description = "수정할 자격 증명 ID") @PathVariable Long credentialId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "수정할 GitHub PAT 정보 (선택적)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "displayName": "Updated-Repo-Access",
                                              "token": "ghp_newtoken0987654321"
                                            }
                                            """
                            )
                    )
            )
            @Valid GithubCredentialUpdateReq req);

    @Operation(summary = "GitHub 자격 증명 삭제", description = "등록된 GitHub 자격 증명을 삭제(Soft Delete)합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"GitHub 자격 증명이 성공적으로 삭제되었습니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "자격 증명 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"해당 GitHub 자격 증명을 찾을 수 없습니다.\"}")))
    })
    ResponseEntity<RsData<Void>> deleteGithubCredential(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(description = "삭제할 자격 증명 ID") @PathVariable Long credentialId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);
}