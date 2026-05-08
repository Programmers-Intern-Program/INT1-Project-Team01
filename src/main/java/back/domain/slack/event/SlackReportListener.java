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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackReportListener {

    private final OrchestratorSessionRepository sessionRepository;
    private final SlackIntegrationRepository integrationRepository;
    private final SlackClient slackClient;

    @Async("slackEventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrchestratorSessionFinished(OrchestratorSessionFinishedEvent event) {
        log.info("오케스트레이터 세션 완료 이벤트 수신. Slack 보고 준비. sessionId: {}", event.sessionId());

        try {
            OrchestratorSession session = sessionRepository.findById(event.sessionId())
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[SlackReportListener#onOrchestratorSessionFinished] session not found by id: " + event.sessionId(),
                            "세션을 찾을 수 없습니다."
                    ));

            if (!"SLACK".equals(session.getSource().name())) {
                return;
            }

            String[] refParts = session.getSourceRef().split(":");
            if (refParts.length < 3) {
                throw new ServiceException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR,
                        "[SlackReportListener#onOrchestratorSessionFinished] invalid sourceRef format: " + session.getSourceRef(),
                        "슬랙 응답 좌표 형식이 올바르지 않습니다."
                );
            }

            String teamId = refParts[0];
            String channelId = refParts[1];
            String threadTs = refParts[2];

            SlackIntegration integration = integrationRepository.findFirstBySlackTeamId(teamId)
                    .orElseThrow(() -> new ServiceException(
                            CommonErrorCode.NOT_FOUND,
                            "[SlackReportListener#onOrchestratorSessionFinished] slack integration not found by teamId: " + teamId,
                            "해당 워크스페이스에 연동된 슬랙 봇 정보가 없습니다."
                    ));

            SlackMessageReq slackReq = SlackMessageReq.builder()
                    .channel(channelId)
                    .text(event.message())
                    .threadTs(threadTs)
                    .build();

            slackClient.sendMessage(integration.getBotToken(), slackReq);
            log.info("오케스트레이터 세션 결과 Slack 전송 완료. sessionId: {}", event.sessionId());

        } catch (ServiceException e) {
            log.error("Slack 보고 전송 중 비즈니스 예외 발생. sessionId: {}", event.sessionId(), e);
        } catch (Exception e) {
            log.error("Slack 보고 전송 중 예상치 못한 오류 발생. sessionId: {}", event.sessionId(), e);
        }
    }
}