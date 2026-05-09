package back.domain.slack.event;

import back.domain.orchestrator.entity.OrchestratorSession;
import back.domain.orchestrator.event.OrchestratorSessionFinishedEvent;
import back.domain.orchestrator.repository.OrchestratorSessionRepository;
import back.domain.slack.client.SlackClient;
import back.domain.slack.dto.request.SlackMessageReq;
import back.domain.slack.entity.SlackIntegration;
import back.domain.slack.repository.SlackIntegrationRepository;
import back.global.exception.CommonErrorCode;
import back.global.exception.ServiceException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackReportListener {

    private static final int DEDUPLICATION_CACHE_MAX_SIZE = 10_000;
    private static final Duration DEDUPLICATION_KEY_TTL = Duration.ofHours(6);

    private final OrchestratorSessionRepository sessionRepository;
    private final SlackIntegrationRepository integrationRepository;
    private final SlackClient slackClient;
    private final SlackReplyDeduplicationCache sentReplyKeys =
            new SlackReplyDeduplicationCache(DEDUPLICATION_CACHE_MAX_SIZE, DEDUPLICATION_KEY_TTL);

    @Async("slackEventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrchestratorSessionFinished(OrchestratorSessionFinishedEvent event) {
        log.info("오케스트레이터 세션 완료 이벤트 수신. Slack 보고 준비. sessionId: {}", event.sessionId());

        try {
            OrchestratorSession session = sessionRepository.findById(event.sessionId())
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[SlackReportListener#onOrchestratorSessionFinished] session not found by id: "
                                    + event.sessionId(),
                            "세션을 찾을 수 없습니다."));

            if (!"SLACK".equals(session.getSource().name())) {
                return;
            }

            sendSlackReply(
                    session.getSourceRef(),
                    event.message(),
                    "orchestrator-session-" + event.sessionId());
            log.info("오케스트레이터 세션 결과 Slack 전송 완료. sessionId: {}", event.sessionId());

        } catch (ServiceException e) {
            log.error("Slack 보고 전송 중 비즈니스 예외 발생. sessionId: {}", event.sessionId(), e);
        } catch (Exception e) {
            log.error("Slack 보고 전송 중 예상치 못한 오류 발생. sessionId: {}", event.sessionId(), e);
        }
    }

    @Async("slackEventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSlackReplyRequested(SlackReplyRequestedEvent event) {
        log.info("Slack 응답 요청 이벤트 수신. sourceRef: {}", event.sourceRef());

        try {
            sendSlackReply(event.sourceRef(), event.message(), event.deduplicationKey());
            log.info("Slack 응답 전송 완료. sourceRef: {}", event.sourceRef());
        } catch (ServiceException exception) {
            log.error("Slack 응답 전송 중 비즈니스 예외 발생. sourceRef: {}", event.sourceRef(), exception);
        } catch (Exception exception) {
            log.error("Slack 응답 전송 중 예상치 못한 오류 발생. sourceRef: {}", event.sourceRef(), exception);
        }
    }

    private void sendSlackReply(String sourceRef, String message, String deduplicationKey) {
        if (isDuplicatedReply(deduplicationKey)) {
            log.info("중복 Slack 응답 요청을 무시합니다. deduplicationKey: {}", deduplicationKey);
            return;
        }

        try {
            SlackSourceRef slackSourceRef = parseSourceRef(sourceRef);
            SlackIntegration integration = integrationRepository.findFirstBySlackTeamId(slackSourceRef.teamId())
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[SlackReportListener#sendSlackReply] slack integration not found by teamId: "
                                    + slackSourceRef.teamId(),
                            "해당 워크스페이스에 연동된 슬랙 봇 정보가 없습니다."));

            SlackMessageReq slackReq = SlackMessageReq.builder()
                    .channel(slackSourceRef.channelId())
                    .text(message)
                    .threadTs(slackSourceRef.threadTs())
                    .build();

            slackClient.sendMessage(integration.getBotToken(), slackReq);
        } catch (RuntimeException exception) {
            releaseReplyKey(deduplicationKey);
            throw exception;
        }
    }

    private boolean isDuplicatedReply(String deduplicationKey) {
        if (deduplicationKey == null) {
            return false;
        }
        return !sentReplyKeys.markIfAbsent(deduplicationKey);
    }

    private void releaseReplyKey(String deduplicationKey) {
        if (deduplicationKey != null) {
            sentReplyKeys.remove(deduplicationKey);
        }
    }

    private SlackSourceRef parseSourceRef(String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[SlackReportListener#parseSourceRef] sourceRef is blank",
                    "슬랙 응답 좌표가 비어 있습니다.");
        }
        String[] refParts = sourceRef.split(":", 3);
        if (refParts.length < 3
                || refParts[0].isBlank()
                || refParts[1].isBlank()
                || refParts[2].isBlank()) {
            throw new ServiceException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    "[SlackReportListener#parseSourceRef] invalid sourceRef format: " + sourceRef,
                    "슬랙 응답 좌표 형식이 올바르지 않습니다.");
        }
        return new SlackSourceRef(refParts[0], refParts[1], refParts[2]);
    }

    private record SlackSourceRef(String teamId, String channelId, String threadTs) {}

    private static final class SlackReplyDeduplicationCache {

        private final int maxSize;
        private final Duration ttl;
        private final Map<String, Instant> keys = new ConcurrentHashMap<>();
        private final Queue<Entry> insertionOrder = new ConcurrentLinkedQueue<>();

        private SlackReplyDeduplicationCache(int maxSize, Duration ttl) {
            this.maxSize = maxSize;
            this.ttl = ttl;
        }

        private boolean markIfAbsent(String key) {
            Instant now = Instant.now();
            removeExpired(now);
            reduceToMaxSize();

            Instant previous = keys.putIfAbsent(key, now);
            if (previous != null) {
                return false;
            }
            insertionOrder.add(new Entry(key, now));
            return true;
        }

        private void remove(String key) {
            keys.remove(key);
        }

        private void removeExpired(Instant now) {
            Instant threshold = now.minus(ttl);
            Entry entry = insertionOrder.peek();
            while (entry != null && entry.createdAt().isBefore(threshold)) {
                insertionOrder.poll();
                keys.remove(entry.key(), entry.createdAt());
                entry = insertionOrder.peek();
            }
        }

        private void reduceToMaxSize() {
            while (keys.size() >= maxSize) {
                Entry oldest = insertionOrder.poll();
                if (oldest == null) {
                    return;
                }
                keys.remove(oldest.key(), oldest.createdAt());
            }
        }

        private record Entry(String key, Instant createdAt) {}
    }
}
