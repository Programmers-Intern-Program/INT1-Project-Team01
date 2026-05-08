package back.domain.orchestrator.adapter;

import back.domain.orchestrator.dto.request.OrchestratorSessionCreateCommand;
import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.service.OrchestratorSessionService;
import back.domain.slack.port.OrchestratorSessionPort;
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

    @Override
    public void createSession(Long workspaceId, String sourceRef, String targetTs, String text) {
        log.info(
                "Slack 요청을 Orchestrator Session으로 변환 시작. workspaceId: {}",
                workspaceId
        );

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