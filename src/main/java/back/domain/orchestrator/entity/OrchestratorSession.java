package back.domain.orchestrator.entity;

import back.domain.orchestrator.enums.OrchestratorSessionSource;
import back.domain.orchestrator.enums.OrchestratorSessionStatus;
import back.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 요청 하나를 기준으로 생성되는 orchestration 단위 엔티티입니다.
 */

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "orchestrator_sessions")
public class OrchestratorSession extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "requested_by_member_id")
    private Long requestedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private OrchestratorSessionSource source;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "user_message", columnDefinition = "TEXT", nullable = false)
    private String userMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrchestratorSessionStatus status;

    @Column(name = "final_report", columnDefinition = "TEXT")
    private String finalReport;

    /**
     * 세션의 상태를 변경합니다.
     */
    public void updateStatus(OrchestratorSessionStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * 최종 보고서 내용을 업데이트하고 상태를 완료(COMPLETED)로 변경합니다.
     */
    public void markAsCompleted(String finalReport) {
        this.finalReport = finalReport;
        this.status = OrchestratorSessionStatus.COMPLETED;
    }
}