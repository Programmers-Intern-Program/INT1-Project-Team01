# Agent Task Template

## 목적

이 문서는 팀원이 에이전트에게 Jira 작업을 맡길 때 사용하는 입력 템플릿이다.
에이전트는 이 템플릿을 먼저 읽고, 부족한 정보만 추가로 확인한다.

## 사용 원칙

- 템플릿을 채울 때 secret, token, credential 원문을 넣지 않는다.
- 작업 범위가 불명확하면 파일 수정 전에 먼저 질문한다.
- 커밋, push, PR은 명시 승인 전 진행하지 않는다.
- 검증을 생략하면 생략 이유를 남긴다.

## 작업 입력

```text
Jira:
- key:
- summary:
- assignee:
- sprint:

Branch:
- baseBranch: develop
- workBranch:

Task:
- type: DOCUMENTATION | CODE_CHANGE | TEST | REVIEW | CHORE
- goal:
- nonGoals:
- doneCriteria:

Allowed scope:
- files:
- packages:
- commands:

Required context:
- JIRA_TASK:
- DESIGN_DECISION:
- CODEBASE:
- TEST:
- TEAM_CONVENTION:
- SECURITY_POLICY:
- EXECUTION_POLICY: harness/EXECUTION_POLICY.md
- TOOL_REGISTRY: harness/TOOL_REGISTRY.md
- VERIFICATION_GATE: harness/VERIFICATION_GATE.md
- TRACE_POLICY: harness/TRACEABILITY.md

Excluded context:
- secrets
- .env
- .git/config
- ignored local docs unless explicitly requested
- unrelated files

Verification plan:
- required:
- optional:
- skipped:

Approval:
- fileEdit: approved | ask-before-edit
- commit: pending
- push: pending
- pr: pending

Expected final report:
- changed files
- context used
- tools used
- verification result
- skipped checks and reason
- remaining risks
```

## 문서 작업 예시

```text
Jira:
- key: IT-000
- summary: [DOCS] 하네스 규칙 문서 정리
- assignee: 담당자 이름
- sprint: MVP1

Branch:
- baseBranch: develop
- workBranch: name_docs/IT-000

Task:
- type: DOCUMENTATION
- goal: 에이전트 작업 규칙 문서를 추가하거나 수정한다.
- nonGoals: 운영 코드, 테스트 코드, DB schema 변경은 하지 않는다.
- doneCriteria: 문서가 팀 공통 규칙으로 읽히고 특정 개인 작업에 묶이지 않는다.

Allowed scope:
- files:
  - harness/
  - .github/PULL_REQUEST_TEMPLATE.md
- packages:
  - none
- commands:
  - rg
  - git status
  - git diff --check

Required context:
- JIRA_TASK: IT-000
- DESIGN_DECISION: harness/DESIGN_CONTEXT.md
- TEAM_CONVENTION: harness/PR_GUIDE.md
- EXECUTION_POLICY: harness/EXECUTION_POLICY.md
- TOOL_REGISTRY: harness/TOOL_REGISTRY.md
- VERIFICATION_GATE: harness/VERIFICATION_GATE.md
- TRACE_POLICY: harness/TRACEABILITY.md

Excluded context:
- secrets
- .env
- .git/config
- ignored local docs unless explicitly requested
- unrelated production code

Verification plan:
- required:
  - git diff --check
  - git status
- optional:
  - rg로 특정 Jira 키나 개인 브랜치명이 공통 문서에 남아있는지 확인
- skipped:
  - ./gradlew test: 문서 변경만 있음

Approval:
- fileEdit: approved
- commit: pending
- push: pending
- pr: pending

Expected final report:
- changed files
- context used
- tools used
- verification result
- skipped checks and reason
- remaining risks
```

## 코드 변경 작업 예시

```text
Jira:
- key: IT-000
- summary: [FEAT] 기능 작업 제목
- assignee: 담당자 이름
- sprint: MVP1

Branch:
- baseBranch: develop
- workBranch: name_feat/IT-000

Task:
- type: CODE_CHANGE
- goal: 구현 목표를 한 문장으로 적는다.
- nonGoals: 해당 범위에서 하지 않을 내용을 적는다.
- doneCriteria: 테스트, API 응답, 예외 처리 등 완료 기준을 적는다.

Allowed scope:
- files:
  - src/main/java/...
  - src/test/java/...
- packages:
  - 관련 package 이름
- commands:
  - rg
  - git status
  - git diff --check
  - ./gradlew test --tests "<TargetTest>"

Required context:
- JIRA_TASK: IT-000
- CODEBASE: 관련 class, service, controller, repository
- TEST: 관련 unit/integration test
- TEAM_CONVENTION: 코드/커밋/PR 규칙
- SECURITY_POLICY: 민감정보와 권한 경계
- EXECUTION_POLICY: harness/EXECUTION_POLICY.md
- TOOL_REGISTRY: harness/TOOL_REGISTRY.md
- VERIFICATION_GATE: harness/VERIFICATION_GATE.md
- TRACE_POLICY: harness/TRACEABILITY.md

Excluded context:
- secrets
- .env
- .git/config
- ignored local docs unless explicitly requested
- unrelated packages

Verification plan:
- required:
  - ./gradlew test --tests "<TargetTest>"
  - git diff --check
- optional:
  - ./gradlew checkstyleMain spotbugsMain
- skipped:
  - 생략할 검증과 이유

Approval:
- fileEdit: approved
- commit: pending
- push: pending
- pr: pending

Expected final report:
- changed files
- context used
- tools used
- verification result
- skipped checks and reason
- remaining risks
```

## 에이전트 시작 절차

에이전트는 작업 입력을 받은 뒤 다음 순서로 시작한다.

1. Jira key, branch, task type, done criteria를 확인한다.
2. `harness/README.md`에서 전체 하네스 흐름을 확인한다.
3. `CONTEXT_PACK.md` 기준으로 필요한 컨텍스트만 수집한다.
4. `EXECUTION_POLICY.md`와 `TOOL_REGISTRY.md`로 허용 작업을 확인한다.
5. 변경 전 의도와 파일 범위를 보고한다.
6. 작업 후 `VERIFICATION_GATE.md` 기준으로 검증한다.
7. `TRACEABILITY.md` 형식으로 최종 보고한다.
