# Verification Gate

## 목적

이 문서는 에이전트 작업 완료 전 실행해야 하는 최소 검증 기준을 정의한다.
MVP에서는 새 CI를 만들지 않고, 현재 저장소의 Gradle/CI 설정을 기준으로 검증한다.

## 현재 CI 기준

`.github/workflows/backend-ci.yml`의 품질 게이트는 다음 명령을 실행한다.

```bash
./gradlew checkstyleMain spotbugsMain test jacocoTestReport jacocoTestCoverageVerification
```

PR 전 로컬 검증은 변경 범위에 맞춰 targeted command를 우선한다.
CI 전체 명령은 필요할 때만 실행한다.

## 변경 유형별 검증

### 문서 변경

필수:

```bash
git diff --check
git status
```

확인:

- 문서 경로가 맞는가?
- 특정 Jira나 개인 브랜치 내용이 공통 문서에 남아 있지 않은가?
- ignored 문서가 실수로 포함되지 않았는가?

### 테스트 변경

필수:

```bash
./gradlew test --tests "<TargetTest>"
git diff --check
```

필요 시:

```bash
./gradlew checkstyleTest
```

### 프로덕션 코드 변경

필수:

```bash
./gradlew test --tests "<NearestTest>"
git diff --check
```

필요 시:

```bash
./gradlew checkstyleMain spotbugsMain
```

공유 로직, 보안, DB, 인증 변경은 더 넓은 테스트를 실행한다.

### API / DB 변경

필수:

- 관련 Controller/Service/Repository 테스트
- migration 또는 schema 영향 확인
- 응답 envelope과 에러 코드 확인

## 실패 처리

검증 실패 시 다음을 남긴다.

```text
command:
status:
failureSummary:
likelyCause:
changedFiles:
nextAction:
```

Agent는 실패 원인을 이해하지 못한 상태로 무작정 수정하지 않는다.

## PR 전 게이트

PR 생성 전 확인한다.

- 최신 `develop` 위에서 작업 브랜치가 rebase됐는가?
- 변경 범위에 맞는 검증을 실행했는가?
- 검증을 생략했다면 이유가 명확한가?
- secret, token, credential이 diff에 없는가?
- PR 본문에 검증 결과를 적을 수 있는가?

## 생략 가능 기준

다음은 생략 가능하다.

- 문서만 변경한 경우 애플리케이션 테스트
- 코드와 무관한 `.gitignore` 변경의 Gradle 테스트
- 기존 전체 Spotless 위반 때문에 전체 `spotlessCheck`가 실패하는 경우

생략 시에는 이유를 작업 요약에 남긴다.

```text
Skipped:
- ./gradlew test: 문서 변경만 있음
```
