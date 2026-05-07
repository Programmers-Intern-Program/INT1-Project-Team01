package back.domain.task.entity;

public enum TaskStatus {
    REQUESTED,  // Task 생성됨
    ASSIGNED,  // 담당 Agent 배정됨
    IN_PROGRESS,  // 작업 진행 중
    WAITING_USER,  // 사용자 확인 필요
    COMPLETED,  // 완료
    FAILED,  // 실패
    CANCELED;  // 취소

    public boolean canChangeTo(TaskStatus nextStatus) {
        if (this == nextStatus) {
            return true;
        }

        return switch (this) {
            case REQUESTED -> nextStatus == ASSIGNED
                    || nextStatus == IN_PROGRESS
                    || nextStatus == CANCELED;

            case ASSIGNED -> nextStatus == IN_PROGRESS
                    || nextStatus == CANCELED;

            case IN_PROGRESS -> nextStatus == WAITING_USER
                    || nextStatus == COMPLETED
                    || nextStatus == FAILED
                    || nextStatus == CANCELED;

            case WAITING_USER -> nextStatus == IN_PROGRESS
                    || nextStatus == COMPLETED
                    || nextStatus == FAILED
                    || nextStatus == CANCELED;

            case COMPLETED, FAILED, CANCELED -> false;
        };
    }
}
