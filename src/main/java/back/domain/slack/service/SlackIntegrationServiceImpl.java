package back.domain.slack.service;

import back.domain.slack.dto.request.SlackIntegrationCreateReq;
import back.domain.slack.dto.request.SlackIntegrationUpdateReq;
import back.domain.slack.dto.response.SlackIntegrationInfoRes;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.domain.workspace.service.WorkspaceAccessValidator;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SlackIntegrationServiceImpl implements SlackIntegrationService {

    private final SlackIntegrationRepository slackIntegrationRepository;
    private final WorkspaceAccessValidator workspaceAccessValidator;

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

    @Override
    @Transactional(readOnly = true)
    public List<SlackIntegrationInfoRes> getSlackIntegrations(Long workspaceId, Long memberId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        return slackIntegrationRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(SlackIntegrationInfoRes::from)
                .toList();
    }

    @Override
    @Transactional
    public SlackIntegrationInfoRes updateSlackIntegration(Long workspaceId, Long integrationId, Long memberId, SlackIntegrationUpdateReq req) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        SlackIntegration integration = slackIntegrationRepository.findById(integrationId)
                .orElseThrow(() -> new ServiceException(CommonErrorCode.NOT_FOUND, "Integration not found", "해당 Slack 연동 정보를 찾을 수 없습니다."));

        if (!integration.getWorkspaceId().equals(workspaceId)) {
            throw new ServiceException(CommonErrorCode.FORBIDDEN, "Workspace mismatch", "해당 워크스페이스의 연동 정보가 아닙니다.");
        }

        // 부분 업데이트 수행
        integration.update(req.slackTeamId(), req.slackChannelId(), req.botToken(), req.signingSecret());

        return SlackIntegrationInfoRes.from(integration);
    }

    @Override
    @Transactional
    public void deleteSlackIntegration(Long workspaceId, Long integrationId, Long memberId) {
        workspaceAccessValidator.requireAdmin(workspaceId, memberId);

        SlackIntegration integration = slackIntegrationRepository.findById(integrationId)
                .orElseThrow(() -> new ServiceException(CommonErrorCode.NOT_FOUND, "Integration not found", "해당 Slack 연동 정보를 찾을 수 없습니다."));

        if (!integration.getWorkspaceId().equals(workspaceId)) {
            throw new ServiceException(CommonErrorCode.FORBIDDEN, "Workspace mismatch", "해당 워크스페이스의 연동 정보가 아닙니다.");
        }

        // @SQLDelete에 의해 자동으로 deleted_at이 갱신됩니다.
        slackIntegrationRepository.delete(integration);
    }
}