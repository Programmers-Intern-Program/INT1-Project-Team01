package back.domain.slack.service;

import back.domain.slack.client.SlackClient;
import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.dto.response.SlackOAuthAccessRes;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import back.global.security.OAuthStateTokenProvider;
import back.global.security.OAuthStateTokenProvider.OAuthStatePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackIntegrationServiceImpl implements SlackIntegrationService {

    private final SlackIntegrationRepository slackIntegrationRepository;
    private final WorkspaceAccessValidator workspaceAccessValidator;
    private final SlackClient slackClient;
    private final OAuthStateTokenProvider oauthStateTokenProvider;

    @Value("${custom.slack.client-id}")
    private String clientId;

    @Value("${custom.slack.client-secret}")
    private String clientSecret;

    @Value("${custom.slack.redirect-uri}")
    private String redirectUri;

    @Override
    @Transactional(readOnly = true)
    public String getOAuthInstallUrl(Long workspaceId, Long memberId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        String stateToken = oauthStateTokenProvider.generateOAuthState(workspaceId, memberId);

        return UriComponentsBuilder.fromUriString("https://slack.com/oauth/v2/authorize")
                .queryParam("client_id", clientId)
                .queryParam("scope", "chat:write,incoming-webhook")
                .queryParam("state", stateToken)
                .queryParam("redirect_uri", redirectUri)
                .build().toUriString();
    }

    @Override
    @Transactional
    public void handleOAuthCallback(String code, String state) {

        OAuthStatePayload payload = oauthStateTokenProvider.parseOAuthState(state);
        Long workspaceId = payload.workspaceId();
        Long memberId = payload.memberId();

        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        SlackOAuthAccessRes response = slackClient.exchangeToken(code, clientId, clientSecret, redirectUri);

        String teamId = response.team().id();
        String channelId = response.incomingWebhook().channelId();
        String botToken = response.accessToken();

        slackIntegrationRepository.findFirstBySlackTeamIdAndSlackChannelId(teamId, channelId)
                .ifPresentOrElse(
                        existing -> {
                            existing.update(null, null, botToken);
                            log.info("Slack 토큰 갱신 완료. WorkspaceId: {}, TeamId: {}, ChannelId: {}", workspaceId, teamId, channelId);
                        },
                        () -> {
                            SlackIntegration integration = SlackIntegration.builder()
                                    .workspaceId(workspaceId)
                                    .slackTeamId(teamId)
                                    .slackChannelId(channelId)
                                    .botToken(botToken)
                                    .createdByMemberId(memberId)
                                    .build();
                            slackIntegrationRepository.save(integration);
                            log.info("Slack OAuth 연동 성공. WorkspaceId: {}, TeamId: {}, ChannelId: {}", workspaceId, teamId, channelId);
                        }
                );
    }

    @Override
    @Transactional
    public SlackIntegrationInfoRes createSlackIntegration(Long workspaceId, Long memberId, SlackIntegrationCreateReq req) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        if (slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelId(req.slackTeamId(), req.slackChannelId())) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[SlackIntegrationServiceImpl#createSlackIntegration] Duplicate integration for team: "
                            + req.slackTeamId() + ", channel: " + req.slackChannelId(),
                    "해당 Slack 채널은 이미 등록되어 있습니다."
            );
        }

        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(workspaceId)
                .slackTeamId(req.slackTeamId())
                .slackChannelId(req.slackChannelId())
                .botToken(req.botToken())
                .createdByMemberId(memberId)
                .build();

        SlackIntegration savedIntegration = slackIntegrationRepository.save(integration);

        return SlackIntegrationInfoRes.from(savedIntegration);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlackIntegrationInfoRes> getSlackIntegrations(Long workspaceId, Long memberId) {
        workspaceAccessValidator.requireMember(workspaceId, memberId);

        return slackIntegrationRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(SlackIntegrationInfoRes::from)
                .toList();
    }

    @Override
    @Transactional
    public SlackIntegrationInfoRes updateSlackIntegration(Long workspaceId, Long integrationId, Long memberId, SlackIntegrationUpdateReq req) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        SlackIntegration integration = slackIntegrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[SlackIntegrationServiceImpl#updateSlackIntegration] Integration not found for id: " + integrationId + " and workspaceId: " + workspaceId,
                        "해당 Slack 연동 정보를 찾을 수 없거나 접근 권한이 없습니다."
                ));

        String targetTeamId = integration.resolveTeamId(req.slackTeamId());
        String targetChannelId = integration.resolveChannelId(req.slackChannelId());

        if (slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelIdAndIdNot(targetTeamId, targetChannelId, integrationId)) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[SlackIntegrationServiceImpl#updateSlackIntegration] Duplicate integration for team: "
                            + targetTeamId + ", channel: " + targetChannelId,
                    "해당 Slack 채널은 이미 등록되어 있습니다."
            );
        }

        integration.update(req.slackTeamId(), req.slackChannelId(), req.botToken());

        return SlackIntegrationInfoRes.from(integration);
    }

    @Override
    @Transactional
    public void deleteSlackIntegration(Long workspaceId, Long integrationId, Long memberId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        SlackIntegration integration = slackIntegrationRepository.findByIdAndWorkspaceId(integrationId, workspaceId)
                .orElseThrow(() -> new ServiceException(
                        CommonErrorCode.NOT_FOUND,
                        "[SlackIntegrationServiceImpl#deleteSlackIntegration] Integration not found for id: " + integrationId + " and workspaceId: " + workspaceId,
                        "해당 Slack 연동 정보를 찾을 수 없거나 접근 권한이 없습니다."
                ));

        slackIntegrationRepository.delete(integration);
    }
}