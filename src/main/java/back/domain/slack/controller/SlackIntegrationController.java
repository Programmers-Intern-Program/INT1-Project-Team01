package back.domain.slack.controller;

import back.domain.slack.controller.docs.SlackIntegrationControllerDocs;
import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.service.SlackIntegrationService;
import back.global.response.RsData;
import back.global.security.AuthenticatedMember;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/slack/integrations")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2", justification = "Spring 서비스 빈은 싱글톤으로 관리되므로 안전합니다."
)
public class SlackIntegrationController implements SlackIntegrationControllerDocs {

    private final SlackIntegrationService slackIntegrationService;

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

    @Override
    @GetMapping
    public ResponseEntity<RsData<List<SlackIntegrationInfoRes>>> getSlackIntegrations(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {

        List<SlackIntegrationInfoRes> res = slackIntegrationService.getSlackIntegrations(
                workspaceId,
                authenticatedMember.memberId()
        );

        return ResponseEntity.ok(new RsData<>(res, "Slack 연동 정보 목록을 성공적으로 조회했습니다."));
    }

    @Override
    @PatchMapping("/{integrationId}")
    public ResponseEntity<RsData<SlackIntegrationInfoRes>> updateSlackIntegration(
            @PathVariable Long workspaceId,
            @PathVariable Long integrationId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestBody @Valid SlackIntegrationUpdateReq req) {

        SlackIntegrationInfoRes res = slackIntegrationService.updateSlackIntegration(
                workspaceId,
                integrationId,
                authenticatedMember.memberId(),
                req
        );

        return ResponseEntity.ok(new RsData<>(res, "Slack 연동 정보가 성공적으로 수정되었습니다."));
    }

    @Override
    @DeleteMapping("/{integrationId}")
    public ResponseEntity<RsData<Void>> deleteSlackIntegration(
            @PathVariable Long workspaceId,
            @PathVariable Long integrationId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {

        slackIntegrationService.deleteSlackIntegration(
                workspaceId,
                integrationId,
                authenticatedMember.memberId()
        );

        return ResponseEntity.ok(new RsData<>(null, "Slack 연동 정보가 성공적으로 삭제되었습니다."));
    }
}