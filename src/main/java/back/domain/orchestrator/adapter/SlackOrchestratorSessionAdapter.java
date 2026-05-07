package back.domain.orchestrator.adapter;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.service.OrchestratorSessionService;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.port.OrchestratorSessionPort;
import back.domain.slack.repository.SlackIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Slack 도메인의 포트(Port)를 구현하여, Slack 이벤트를 Orchestrator 세션 생성 요청으로 변환하는 어댑터입니다.
 */

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class SlackOrchestratorSessionAdapter implements OrchestratorSessionPort {

    private final OrchestratorSessionService orchestratorSessionService;
    private final SlackIntegrationRepository slackIntegrationRepository;

    @Override
    public void createSession(String teamId, String channelId, String targetTs, String text) {
        log.info(
                "Slack 요청을 Orchestrator Session으로 변환 시작. team: {}, channel: {}",
                teamId, channelId
        );

        SlackIntegration integration = slackIntegrationRepository
                .findFirstBySlackTeamIdAndSlackChannelId(teamId, channelId)
                .orElseThrow(
                        () -> new IllegalStateException("해당 Slack 채널에 매핑된 Workspace 정보를 찾을 수 없습니다.")
                );

        Long workspaceId = integration.getWorkspace().getId();

        String sourceRef = String.format("%s:%s:%s", teamId, channelId, targetTs);

        OrchestratorSessionCreateCommand command = OrchestratorSessionCreateCommand.builder()
                .workspaceId(workspaceId)
                .requestedByMemberId(null)
                .source(OrchestratorSessionSource.SLACK)
                .sourceRef(sourceRef)
                .userMessage(text)
                .build();

        orchestratorSessionService.createSession(command);
    }
}