package back.domain.agent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import back.domain.agent.dto.request.OpenClawAgentCreateReq;
import back.domain.agent.dto.response.OpenClawAgentCreateRes;
import back.domain.agent.service.AgentProvisioningService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentProvisioningService agentProvisioningService;

    @PostMapping
    public ResponseEntity<RsData<OpenClawAgentCreateRes>> createAgent(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid OpenClawAgentCreateReq request) {
        OpenClawAgentCreateRes response =
                agentProvisioningService.createAgent(workspaceId, authenticatedMember.memberId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response, "Agent 생성 요청이 처리되었습니다."));
    }
}
