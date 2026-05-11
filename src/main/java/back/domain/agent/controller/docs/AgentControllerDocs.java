package back.domain.agent.controller.docs;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.AgentInfoRes;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;
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

@Tag(name = "Agent", description = "OpenClaw Agent 생성/조회 API")
public interface AgentControllerDocs {

    @Operation(summary = "Agent 생성", description = "워크스페이스에 OpenClaw Agent를 생성하고 Skill 파일을 동기화합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Agent 생성 요청 처리 성공"),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패"),
            @ApiResponse(responseCode = "403", description = "워크스페이스 관리자 권한 없음")
    })
    ResponseEntity<RsData<OpenClawAgentCreateRes>> createAgent(
            @Parameter(description = "워크스페이스 ID", example = "1") @PathVariable Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Agent 생성 정보",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "name": "Backend Agent",
                      "category": "BACKEND",
                      "workspacePath": "~/.openclaw/workspace-1",
                      "emoji": "tool",
                      "skillFiles": [
                        {
                          "fileName": "AGENTS.md",
                          "content": "You are a backend agent."
                        }
                      ]
                    }
                    """)))
            @Valid OpenClawAgentCreateReq request);

    @Operation(summary = "Agent 목록 조회", description = "워크스페이스에 생성된 Agent 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Agent 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "data": [
                        {
                          "agentId": 1,
                          "workspaceId": 1,
                          "name": "Backend Agent",
                          "category": "BACKEND",
                          "openClawAgentId": "workspace-1-agent-1",
                          "workspacePath": "~/.openclaw/workspace-1",
                          "status": "READY",
                          "syncError": null,
                          "createdByMemberId": 10,
                          "skillFiles": []
                        }
                      ],
                      "message": "Agent 목록 조회 성공"
                    }
                    """))),
            @ApiResponse(responseCode = "403", description = "워크스페이스 접근 권한 없음")
    })
    ResponseEntity<RsData<List<AgentInfoRes>>> listAgents(
            @Parameter(description = "워크스페이스 ID", example = "1") @PathVariable Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(summary = "Agent 상세 조회", description = "워크스페이스 내 특정 Agent의 상세 정보와 Skill 파일 동기화 상태를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Agent 상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                    {
                      "data": {
                        "agentId": 1,
                        "workspaceId": 1,
                        "name": "Backend Agent",
                        "category": "BACKEND",
                        "openClawAgentId": "workspace-1-agent-1",
                        "workspacePath": "~/.openclaw/workspace-1",
                        "status": "READY",
                        "syncError": null,
                        "createdByMemberId": 10,
                        "skillFiles": [
                          {
                            "skillFileId": 1,
                            "fileName": "AGENTS.md",
                            "syncStatus": "SYNCED",
                            "syncError": null
                          }
                        ]
                      },
                      "message": "Agent 상세 조회 성공"
                    }
                    """))),
            @ApiResponse(responseCode = "403", description = "워크스페이스 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "Agent를 찾을 수 없음")
    })
    ResponseEntity<RsData<AgentInfoRes>> getAgent(
            @Parameter(description = "워크스페이스 ID", example = "1") @PathVariable Long workspaceId,
            @Parameter(description = "Agent ID", example = "1") @PathVariable Long agentId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember);
}
