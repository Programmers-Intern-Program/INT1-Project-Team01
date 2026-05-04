package back.domain.task.domain;

public enum TaskType {
    // 업무 종류
    CODE_REVIEW,    // 코드 리뷰
    PR_REVIEW,    // 깃 PR 리뷰
    BUG_FIX,    // 버그 분석
    FEATURE_IMPLEMENTATION,    // 기능 구현
    REFACTORING,    // 리팩토링 작업
    TEST_CREATION,    // 테스트코드 작성
    DOCUMENTATION,    // 문서 작업
    PR_CREATION,    //  PR 생성

    OTHER   // 위 타입으로 분류하기 어려운 기타 업무
}