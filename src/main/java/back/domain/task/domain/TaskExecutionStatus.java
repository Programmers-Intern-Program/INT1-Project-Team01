package back.domain.task.domain;

public enum TaskExecutionStatus {
    PENDING,   // 실행 대기
    RUNNING,   // 실행 중
    SUCCESS,   // 실행 성공
    FAILED,    // 실행 실패
    CANCELED   // 실행 취소
}