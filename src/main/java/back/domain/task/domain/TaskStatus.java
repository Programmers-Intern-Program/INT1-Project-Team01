package back.domain.task.domain;

public enum TaskStatus {
    REQUESTED,  // Task 생성됨
    ASSIGNED,  // 담당 Agent 배정됨
    IN_PROGRESS,  // 작업 진행 중
    WAITING_USER,  // 사용자 확인 필요
    COMPLETED,  // 완료
    FAILED,  // 실패
    CANCELED  // 취소
}
