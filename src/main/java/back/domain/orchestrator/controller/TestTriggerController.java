// 경로: src/main/java/back/domain/orchestrator/controller/TestTriggerController.java
package back.domain.orchestrator.controller;

import back.domain.orchestrator.event.OrchestratorSessionFinishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * [임시 스텁] Postman 로컬 테스트 전용 컨트롤러입니다.
 * TODO: [IT-9] 삭제 필요
 */
@RestController
@RequiredArgsConstructor
public class TestTriggerController {

    private final ApplicationEventPublisher eventPublisher;

    // Postman에서 보낼 JSON 바디를 받기 위한 임시 DTO
    public record TriggerReq(Long sessionId, String message) {}

    @PostMapping("/test/trigger-slack")
    public String triggerSlackReport(@RequestBody TriggerReq req) {
        // Postman으로 받은 데이터를 그대로 이벤트로 발행합니다.
        eventPublisher.publishEvent(new OrchestratorSessionFinishedEvent(req.sessionId(), req.message()));

        return "✅ 오케스트레이터 세션 완료 이벤트 발행 성공!\n전달된 메시지: " + req.message();
    }
}