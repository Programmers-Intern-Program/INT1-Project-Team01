package back.domain.agent.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import back.domain.agent.controller.docs.AgentControllerDocs;
import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.AgentInfoRes;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;
import back.domain.agent.service.AgentQueryService;
import back.domain.agent.service.AgentProvisioningService;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/agents")
@RequiredArgsConstructor
public class AgentController implements AgentControllerDocs {

    private final AgentProvisioningService agentProvisioningService;
    private final AgentQueryService agentQueryService;

    @Override
    @PostMapping
    public ResponseEntity<RsData<OpenClawAgentCreateRes>> createAgent(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid OpenClawAgentCreateReq request) {
        Long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        OpenClawAgentCreateRes response =
                agentProvisioningService.createAgent(workspaceId, memberId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response, "Agent 생성 요청이 처리되었습니다."));
    }

    @Override
    @GetMapping
    public ResponseEntity<RsData<List<AgentInfoRes>>> listAgents(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(new RsData<>(
                agentQueryService.listAgents(workspaceId, memberId),
                "Agent 목록 조회 성공"));
    }

    @Override
    @GetMapping("/{agentId}")
    public ResponseEntity<RsData<AgentInfoRes>> getAgent(
            @PathVariable Long workspaceId,
            @PathVariable Long agentId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        Long memberId = resolveAuthenticatedMemberId(authenticatedMember);
        return ResponseEntity.ok(new RsData<>(
                agentQueryService.getAgent(workspaceId, memberId, agentId),
                "Agent 상세 조회 성공"));
    }

    private Long resolveAuthenticatedMemberId(AuthenticatedMember authenticatedMember) {
        if (authenticatedMember == null) {
            throw new ServiceException(
                    CommonErrorCode.UNAUTHORIZED,
                    "[AgentController#resolveAuthenticatedMemberId] authenticated member is missing",
                    CommonErrorCode.UNAUTHORIZED.defaultMessage());
        }
        return authenticatedMember.memberId();
    }
}
