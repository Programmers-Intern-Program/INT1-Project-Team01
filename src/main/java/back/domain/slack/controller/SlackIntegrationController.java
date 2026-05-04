package back.domain.slack.controller;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.service.SlackIntegrationService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/slack/integrations")
@RequiredArgsConstructor
public class SlackIntegrationController {

    private final SlackIntegrationService slackIntegrationService;

    /**
     * Workspace의 Slack 연동 정보를 신규 등록합니다.
     */
    @PostMapping
    public ResponseEntity<RsData<SlackIntegrationInfoRes>> createSlackIntegration(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid SlackIntegrationCreateReq req) {

        SlackIntegrationInfoRes res = slackIntegrationService.createSlackIntegration(
                workspaceId,
                authenticatedMember.memberId(),
                req
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(res, "Slack 연동 정보가 성공적으로 등록되었습니다."));
    }
}