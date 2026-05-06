package back.domain.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import back.domain.gateway.dto.request.WorkspaceGatewayBindingReq;
import back.domain.gateway.dto.response.WorkspaceGatewayBindingRes;
import back.domain.gateway.service.WorkspaceGatewayBindingService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/gateway/binding")
@RequiredArgsConstructor
public class WorkspaceGatewayBindingController {

    private final WorkspaceGatewayBindingService workspaceGatewayBindingService;

    @PostMapping
    public ResponseEntity<RsData<WorkspaceGatewayBindingRes>> bindExternalGateway(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid WorkspaceGatewayBindingReq request) {
        WorkspaceGatewayBindingRes response = workspaceGatewayBindingService.bindExternalGateway(
                workspaceId, authenticatedMember.memberId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response, "Workspace Gateway 설정이 저장되었습니다."));
    }
}
