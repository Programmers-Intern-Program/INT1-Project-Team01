# Traceability

## 목적

이 문서는 에이전트 작업 결과를 추적하고, 실패 원인을 재현 가능하게 남기는 최소 기준을 정의한다.
MVP에서는 별도 로그 서버나 평가 플랫폼을 만들지 않고, 작업 요약과 PR 본문에 사람이 확인 가능한 기록을 남긴다.

## 추적 대상

작업 완료 시 다음을 남긴다.

- Jira 이슈 키
- 브랜치 이름
- 변경 파일
- 사용한 Context Pack 요약
- 사용한 주요 도구
- 실행한 검증 명령
- 생략한 검증과 이유
- 남은 위험 또는 확인할 점
- 커밋, push, PR 승인 여부

## 작업 요약 형식

```text
Jira:
Branch:
Changed files:
Context used:
Tools used:
Verification:
Skipped:
Risks:
Approval:
```

예:

```text
Jira: IT-000
Branch: name_docs/IT-000
Changed files:
- harness/README.md
Context used:
- JIRA_TASK: IT-000
- TEAM_CONVENTION: harness/PR_GUIDE.md
Tools used:
- rg
- git diff --check
Verification:
- git diff --check: passed
Skipped:
- app test: docs only
Risks:
- none
Approval:
- commit/push/PR: pending user approval
```

## 재현 기준

실패 또는 리뷰 필요 작업은 다음 정보가 있어야 재현 가능하다고 본다.

- 입력 Jira Task
- 기준 브랜치
- 작업 브랜치
- 관련 파일
- 실행 명령
- 실패 출력 요약
- 생략한 컨텍스트와 이유

Mock tool response나 regression suite는 MVP에서 만들지 않는다.
필요해지는 시점에 별도 구현 작업으로 분리한다.

## Artifact 기준

저장하거나 요약할 수 있는 것:

- git diff 요약
- changed files
- test result summary
- PR URL
- Agent final report
- 실패 명령의 핵심 출력

저장하지 않는 것:

- PAT 원문
- Slack token 원문
- Gateway token 원문
- private key
- credential helper 내용
- `.env` 내용

## 이상 행동 감지 기준

다음 이벤트가 발생하면 중단하고 사용자에게 보고한다.

- 금지 파일 접근 필요
- 민감정보로 보이는 값이 diff 또는 출력에 등장
- 같은 명령 2회 이상 반복 실패
- 작업 범위 밖 파일 변경
- 예상보다 많은 파일 변경
- destructive Git 작업 필요
- 승인 없이 commit, push, PR이 필요한 상황

## 운영 모니터링 보류 기준

다음은 MVP 문서 단계에서는 구현하지 않는다.

- 중앙 집중식 로그 수집
- Agent별 성공률/실패율 대시보드
- 자동 이상 탐지 엔진
- replay runner
- regression evaluation suite

이 기준이 필요한 시점은 다음이다.

- Agent 실행이 실제 TaskExecution으로 저장되기 시작한 경우
- 동일 실패가 반복되어 원인 분석 비용이 커진 경우
- 여러 Agent가 병렬로 실행되어 운영자가 상태를 보기 어려운 경우
