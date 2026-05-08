package back.domain.slack.event;

import back.domain.slack.entity.SlackEventLog;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackEventLogRepository;
import back.domain.slack.port.OrchestratorSessionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * SlackEventReceivedEvent를 구독하여 비동기로 메시지를 파싱하고 비즈니스 로직으로 넘기는 핸들러입니다.
 */

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification ="스프링 DI 컨테이너가 주입하는 빈이므로 외부 변조 위험 없음"
)
public class SlackEventHandler {

    private final SlackEventLogRepository slackEventLogRepository;
    private final OrchestratorSessionPort orchestratorSessionPort;
    private final JsonMapper jsonMapper;

    private static final Pattern SLACK_MENTION_PATTERN = Pattern.compile("<@[A-Z0-9]+>");

    /**
     * 비동기 환경에서 슬랙 이벤트를 파싱하고 Orchestrator 세션 생성을 요청합니다.
     *
     * <p>@Async로 별도 스레드에서 실행되므로 호출자 트랜잭션과 분리됩니다.
     * @Transactional은 이 메서드 단독으로 새 트랜잭션을 열어 eventLog 상태 변경(markAsProcessed 등)을
     * DB에 반영하기 위해 사용합니다.
     */
    @Async("slackEventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSlackEvent(SlackEventReceivedEvent event) {
        SlackEventLog eventLog = slackEventLogRepository.findById(event.eventLogId())
                .orElseThrow(() -> new IllegalArgumentException("이벤트 로그를 찾을 수 없습니다. ID: " + event.eventLogId()));

        try {
            SlackIntegration integration = eventLog.getIntegration();
            if (integration == null) {
                log.warn("SlackEventLog에 연결된 Integration 정보가 없습니다. logId: {}", event.eventLogId());
                eventLog.markAsIgnored("SlackEventLog에 연결된 Integration 정보가 없습니다.");
                return;
            }

            Long workspaceId = integration.getWorkspace().getId();

            JsonNode rootNode = jsonMapper.readTree(eventLog.getRawPayload());
            JsonNode eventNode = rootNode.path("event");

            String teamId = rootNode.path("team_id").asString();
            String channelId = eventNode.path("channel").asString();
            String rawText = eventNode.path("text").asString();
            String ts = eventNode.path("ts").asString();

            JsonNode threadTsNode = eventNode.path("thread_ts");
            String threadTs = !threadTsNode.isMissingNode() ? threadTsNode.asString() : null;

            String cleanText = sanitizeText(rawText);
            String targetTs = (threadTs != null && !threadTs.isBlank()) ? threadTs : ts;
            String sourceRef = String.format("%s:%s:%s", teamId, channelId, targetTs);

            if (cleanText.isBlank()) {
                eventLog.markAsIgnored("파싱 후 실행할 명령어(Text)가 존재하지 않습니다.");
                return;
            }

            orchestratorSessionPort.createSession(workspaceId, sourceRef, targetTs, cleanText);

            eventLog.markAsProcessed();
            log.info(
                    "Slack 이벤트 파싱 및 세션 생성 요청 완료. targetTs: {}",
                    targetTs
            );

        } catch (Exception e) {
            log.error(
                    "Slack 이벤트 비동기 처리 중 오류 발생. logId: {}",
                    event.eventLogId(), e
            );
            eventLog.markAsFailed(e.getMessage());
        }
    }

    /**
     * Slack 메시지에서 멘션 태그를 제거하고 앞뒤 공백을 다듬습니다.
     */
    private String sanitizeText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        return SLACK_MENTION_PATTERN.matcher(rawText).replaceAll("").strip();
    }
}