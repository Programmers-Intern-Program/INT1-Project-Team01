package back.domain.slack.controller.docs;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
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

@Tag(name = "Slack Integration", description = "Slack 연동 정보 관리 API")
public interface SlackIntegrationControllerDocs {

    @Operation(summary = "Slack 연동 정보 신규 등록", description = "Workspace의 Slack 연동 정보(Bot Token, Signing Secret 등)를 신규 등록합니다.")
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
                                                "slackTeamId": "T12345",
                                                "slackChannelId": "C12345",
                                                "maskedBotToken": "xoxb-****1234"
                                              },
                                              "message": "Slack 연동 정보가 성공적으로 등록되었습니다."
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 검증 실패 (필수 값 누락)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"Slack Team ID는 필수입니다.\"}"))),
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
                    description = "중복된 연동 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"해당 Slack 채널은 이미 Workspace에 연동되어 있습니다.\"}")))
    })
    ResponseEntity<RsData<SlackIntegrationInfoRes>> createSlackIntegration(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "등록할 Slack 연동 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "slackTeamId": "T12345",
                                              "slackChannelId": "C12345",
                                              "botToken": "xoxb-real-token",
                                              "signingSecret": "real-secret-key"
                                            }
                                            """
                            )
                    )
            )
            @Valid SlackIntegrationCreateReq req);

    @Operation(summary = "Slack 연동 정보 목록 조회", description = "해당 Workspace에 등록된 모든 Slack 연동 정보를 조회합니다.")
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
                                                  "slackTeamId": "T12345",
                                                  "slackChannelId": "C12345",
                                                  "maskedBotToken": "xoxb-****1234"
                                                }
                                              ],
                                              "message": "Slack 연동 정보 목록을 성공적으로 조회했습니다."
                                            }
                                            """)))
    })
    ResponseEntity<RsData<List<SlackIntegrationInfoRes>>> getSlackIntegrations(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(summary = "Slack 연동 정보 부분 수정", description = "등록된 Slack 연동 정보를 부분 수정합니다. 값이 있는 필드만 업데이트됩니다.")
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
                                                "slackTeamId": "T12345",
                                                "slackChannelId": "C99999",
                                                "maskedBotToken": "xoxb-****5678"
                                              },
                                              "message": "Slack 연동 정보가 성공적으로 수정되었습니다."
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "연동 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"해당 Slack 연동 정보를 찾을 수 없습니다.\"}")))
    })
    ResponseEntity<RsData<SlackIntegrationInfoRes>> updateSlackIntegration(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(description = "수정할 연동 정보 ID") @PathVariable Long integrationId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "수정할 Slack 연동 정보 (선택적)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "slackChannelId": "C99999",
                                              "botToken": "xoxb-new-token"
                                            }
                                            """
                            )
                    )
            )
            @Valid SlackIntegrationUpdateReq req);

    @Operation(summary = "Slack 연동 정보 삭제", description = "등록된 Slack 연동 정보를 삭제(Soft Delete)합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"Slack 연동 정보가 성공적으로 삭제되었습니다.\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "연동 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"data\":null,\"message\":\"해당 Slack 연동 정보를 찾을 수 없습니다.\"}")))
    })
    ResponseEntity<RsData<Void>> deleteSlackIntegration(
            @Parameter(description = "대상 워크스페이스 ID") @PathVariable Long workspaceId,
            @Parameter(description = "삭제할 연동 정보 ID") @PathVariable Long integrationId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);
}