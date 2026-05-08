package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlackIntegrationServiceImpl implements SlackIntegrationService {

    private final SlackIntegrationRepository slackIntegrationRepository;
    private final WorkspaceAccessValidator workspaceAccessValidator; // workspaceRepository 삭제됨

    @Override
    @Transactional
    public SlackIntegrationInfoRes createSlackIntegration(Long workspaceId, Long memberId, SlackIntegrationCreateReq req) {

        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        if (slackIntegrationRepository.existsBySlackTeamIdAndSlackChannelId(req.slackTeamId(), req.slackChannelId())) {
            throw new ServiceException(
                    CommonErrorCode.CONFLICT,
                    "[SlackIntegrationServiceImpl#createSlackIntegration] Duplicate integration for team: "
                            + req.slackTeamId() + ", channel: " + req.slackChannelId(),
                    "해당 Slack 채널은 이미 Workspace에 연동되어 있습니다."
            );
        }

        SlackIntegration integration = SlackIntegration.builder()
                .workspaceId(workspaceId)
                .slackTeamId(req.slackTeamId())
                .slackChannelId(req.slackChannelId())
                .botToken(req.botToken())
                .signingSecret(req.signingSecret())
                .createdByMemberId(memberId)
                .build();

        SlackIntegration savedIntegration = slackIntegrationRepository.save(integration);

        return SlackIntegrationInfoRes.from(savedIntegration);
    }
}