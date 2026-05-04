package back.domain.workspace.controller.docs;

import java.util.List;

import back.domain.workspace.dto.request.CreateWorkspaceInviteReq;
import back.domain.workspace.dto.request.CreateWorkspaceReq;
import back.domain.workspace.dto.request.UpdateWorkspaceRoleReq;
import back.domain.workspace.dto.request.UpdateWorkspaceReq;
import back.domain.workspace.dto.response.WorkspaceInviteInfoRes;
import back.domain.workspace.dto.response.WorkspaceMemberInfoRes;
import back.domain.workspace.dto.response.WorkspaceInfoRes;
import back.domain.workspace.dto.response.WorkspaceSummaryInfoRes;
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

@Tag(name = "Workspace", description = "워크스페이스 생성/조회/수정/멤버 관리/초대 링크 생성 API")
public interface WorkspaceControllerDocs {

    @Operation(summary = "워크스페이스 생성", description = "새로운 워크스페이스를 생성합니다. 생성자는 자동으로 ADMIN 역할을 부여받습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "워크스페이스 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": {
                        "workspaceId": 1,
                        "name": "개발팀 워크스페이스",
                        "description": "백엔드 개발팀 협업 공간입니다.",
                        "createdByMemberId": 101,
                        "myRole": "ADMIN",
                        "createdAt": "2025-01-01T09:00:00",
                        "updatedAt": "2025-01-01T09:00:00"
                      },
                      "message": "워크스페이스가 생성되었습니다."
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
                      "message": "name-NotBlank-워크스페이스 이름은 필수입니다."
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
    ResponseEntity<RsData<WorkspaceInfoRes>> create(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "워크스페이스 생성 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "name": "개발팀 워크스페이스",
                      "description": "백엔드 개발팀 협업 공간입니다."
                    }
                    """)))
            @Valid CreateWorkspaceReq request);

    @Operation(summary = "내 워크스페이스 목록 조회", description = "로그인한 사용자가 속한 모든 워크스페이스 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": [
                        {
                          "workspaceId": 1,
                          "name": "개발팀 워크스페이스",
                          "description": "백엔드 개발팀 협업 공간입니다.",
                          "myRole": "ADMIN",
                          "agentCount": 0,
                          "runningTaskCount": 0,
                          "createdAt": "2025-01-01T09:00:00"
                        }
                      ],
                      "message": "워크스페이스 목록 조회 성공"
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
    ResponseEntity<RsData<List<WorkspaceSummaryInfoRes>>> listMine(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(summary = "워크스페이스 상세 조회", description = "워크스페이스 ID로 상세 정보를 조회합니다. 워크스페이스 멤버만 조회할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": {
                        "workspaceId": 1,
                        "name": "개발팀 워크스페이스",
                        "description": "백엔드 개발팀 협업 공간입니다.",
                        "createdByMemberId": 101,
                        "myRole": "ADMIN",
                        "createdAt": "2025-01-01T09:00:00",
                        "updatedAt": "2025-01-01T09:00:00"
                      },
                      "message": "워크스페이스 조회 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "워크스페이스 멤버가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 접근 권한이 없습니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<WorkspaceInfoRes>> getWorkspace(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "워크스페이스 ID", example = "1") long workspaceId);

    @Operation(summary = "워크스페이스 수정", description = "워크스페이스의 이름과 설명을 수정합니다. ADMIN 역할만 가능합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": {
                        "workspaceId": 1,
                        "name": "수정된 워크스페이스",
                        "description": "수정된 설명입니다.",
                        "createdByMemberId": 101,
                        "myRole": "ADMIN",
                        "createdAt": "2025-01-01T09:00:00",
                        "updatedAt": "2025-01-02T10:00:00"
                      },
                      "message": "워크스페이스 수정 성공"
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
                      "message": "name-NotBlank-워크스페이스 이름은 필수입니다."
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 관리자 권한이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<WorkspaceInfoRes>> updateWorkspace(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "워크스페이스 ID", example = "1") long workspaceId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "수정할 워크스페이스 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "name": "수정된 워크스페이스",
                      "description": "수정된 설명입니다."
                    }
                    """)))
            @Valid UpdateWorkspaceReq request);

    @Operation(summary = "워크스페이스 멤버 목록 조회", description = "워크스페이스에 속한 모든 멤버 목록을 조회합니다. 워크스페이스 멤버만 조회할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "멤버 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": [
                        {
                          "memberId": 101,
                          "name": "홍길동",
                          "email": "user101@example.com",
                          "role": "ADMIN",
                          "joinedAt": "2025-01-01T09:00:00"
                        },
                        {
                          "memberId": 102,
                          "name": "김철수",
                          "email": "user102@example.com",
                          "role": "MEMBER",
                          "joinedAt": "2025-01-02T10:00:00"
                        }
                      ],
                      "message": "워크스페이스 멤버 목록 조회 성공"
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "워크스페이스 멤버가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 접근 권한이 없습니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<List<WorkspaceMemberInfoRes>>> listMembers(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "워크스페이스 ID", example = "1") long workspaceId);

    @Operation(summary = "워크스페이스 멤버 역할 변경", description = "워크스페이스 멤버의 역할을 변경합니다. ADMIN 역할만 가능하며, 마지막 ADMIN은 변경할 수 없습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "역할 변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 멤버 역할 변경 성공\"}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패 또는 마지막 ADMIN 변경 시도",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스의 마지막 관리자는 변경하거나 제거할 수 없습니다.\"}"))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 관리자 권한이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스 또는 멤버를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<Void>> changeMemberRole(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "워크스페이스 ID", example = "1") long workspaceId,
            @Parameter(description = "역할을 변경할 멤버 ID", example = "102") long memberId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "변경할 역할 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "role": "MEMBER"
                    }
                    """)))
            @Valid UpdateWorkspaceRoleReq request);

    @Operation(summary = "워크스페이스 멤버 삭제", description = "워크스페이스에서 멤버를 제거합니다. ADMIN 역할만 가능하며, 마지막 ADMIN은 제거할 수 없습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "멤버 삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 멤버 삭제 성공\"}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "마지막 ADMIN 삭제 시도",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스의 마지막 관리자는 변경하거나 제거할 수 없습니다.\"}"))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 관리자 권한이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스 또는 멤버를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<Void>> removeMember(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "워크스페이스 ID", example = "1") long workspaceId,
            @Parameter(description = "삭제할 멤버 ID", example = "102") long memberId);

    @Operation(summary = "워크스페이스 초대 링크 생성", description = "워크스페이스 초대 링크를 생성합니다. ADMIN 역할만 가능하며, 만료일은 기본 7일입니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "초대 링크 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = """
                    {
                      "data": {
                        "inviteId": 10,
                        "token": "550e8400-e29b-41d4-a716-446655440000",
                        "inviteUrl": "http://localhost:8080/api/v1/invites/550e8400-e29b-41d4-a716-446655440000/accept",
                        "expiresAt": "2025-01-08T09:00:00"
                      },
                      "message": "초대 링크가 생성되었습니다."
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
                      "message": "expiresInDays-Min-초대 링크 만료일은 1일 이상이어야 합니다."
                    }
                    """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스 관리자 권한이 필요합니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"data\":null,\"message\":\"워크스페이스가 존재하지 않습니다.\"}")))
    })
    ResponseEntity<RsData<WorkspaceInviteInfoRes>> createInviteLink(
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @Parameter(description = "워크스페이스 ID", example = "1") long workspaceId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "초대 링크 생성 옵션 (만료일, 부여할 역할)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "expiresInDays": 7,
                      "role": "MEMBER"
                    }
                    """)))
            @Valid CreateWorkspaceInviteReq request);
}
