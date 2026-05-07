package back.domain.slack.event;

import back.domain.slack.entity.SlackEventLog;
import back.domain.slack.repository.SlackEventLogRepository;
import back.domain.slack.port.OrchestratorSessionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
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
     */
    @Async("slackEventTaskExecutor")
    @EventListener
    @Transactional
    public void handleSlackEvent(SlackEventReceivedEvent event) {
        SlackEventLog eventLog = slackEventLogRepository.findById(event.eventLogId())
                .orElseThrow(() -> new IllegalArgumentException("이벤트 로그를 찾을 수 없습니다. ID: " + event.eventLogId()));

        try {
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

            if (cleanText.isBlank()) {
                eventLog.markAsIgnored("파싱 후 실행할 명령어(Text)가 존재하지 않습니다.");
                return;
            }

            orchestratorSessionPort.createSession(teamId, channelId, targetTs, cleanText);

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