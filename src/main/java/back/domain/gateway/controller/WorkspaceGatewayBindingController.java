package back.domain.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.request.WorkspaceGatewayConnectionTestReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.dto.response.WorkspaceGatewayConnectionTestRes;
import back.domain.gateway.dto.response.WorkspaceGatewayStatusRes;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/gateway")
@RequiredArgsConstructor
public class WorkspaceGatewayBindingController {

    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @GetMapping
    public ResponseEntity<RsData<WorkspaceGatewayStatusRes>> getWorkspaceGatewayStatus(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        WorkspaceGatewayStatusRes response = workspaceGatewayBindingService.getWorkspaceGatewayStatus(
                workspaceId, authenticatedMember.memberId());

        return ResponseEntity.ok(new RsData<>(response, "Workspace Gateway 상태를 조회했습니다."));
    }

    @PostMapping("/binding")
    public ResponseEntity<RsData<WorkspaceGatewayBindingRes>> bindExternalGateway(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid WorkspaceGatewayBindingReq request) {
        WorkspaceGatewayBindingRes response = workspaceGatewayBindingService.bindExternalGateway(
                workspaceId, authenticatedMember.memberId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response, "Workspace Gateway 설정이 저장되었습니다."));
    }

    @PostMapping("/connection-test")
    public ResponseEntity<RsData<WorkspaceGatewayConnectionTestRes>> testExternalGateway(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid WorkspaceGatewayConnectionTestReq request) {
        WorkspaceGatewayConnectionTestRes response = workspaceGatewayBindingService.testExternalGateway(
                workspaceId, authenticatedMember.memberId(), request);

        return ResponseEntity.ok(new RsData<>(response, "Workspace Gateway 연결 테스트가 완료되었습니다."));
    }
}
