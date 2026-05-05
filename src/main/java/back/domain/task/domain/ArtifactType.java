package back.domain.task.domain;

public enum ArtifactType {
    PR_URL,       // PR 링크
    COMMIT_HASH,  // 커밋 해시
    FILE_PATH,    // 파일 경로
    DIFF,         // 코드 변경 diff
    LOG_FILE,     // 로그 파일
    RESULT_FILE,  // 결과 파일
    OTHER         // 기타 산출물
}