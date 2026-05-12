package back.domain.workspace.controller;

import back.domain.workspace.dto.request.WorkspacePresencePositionUpdateReq;
import back.domain.workspace.service.WorkspacePresenceService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/presence")
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음")
public class WorkspacePresenceController {
    private final WorkspacePresenceService workspacePresenceService;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable long workspaceId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String accessToken) {
        return workspacePresenceService.stream(workspaceId, authorizationHeader, token, accessToken);
    }

    @PatchMapping("/me/position")
    public ResponseEntity<RsData<Void>> updateMyPosition(
            @PathVariable long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody WorkspacePresencePositionUpdateReq request) {
        workspacePresenceService.updateMyPosition(workspaceId, authenticatedMember, request);
        return ResponseEntity.ok(new RsData<>("Presence 위치가 갱신되었습니다."));
    }
}
