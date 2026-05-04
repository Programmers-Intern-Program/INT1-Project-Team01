# Context Pack Rules

## 목적

Context Pack은 에이전트가 특정 Jira Task를 수행하기 전에 받아야 하는 작업 맥락 묶음이다.
목표는 필요한 정보를 안정적으로 주입하되, 불필요한 문서와 민감정보를 과하게 넣지 않는 것이다.

## 구성 단위

Context Pack은 다음 단위로 구성한다.

```text
taskKey
taskSummary
taskType
agentRole
branchName
baseBranch
repositoryScope
contextSources
excludedSources
verificationPlan
contextTrace
```

`contextSources`의 각 항목은 다음 정보를 가진다.

```text
sourceType
title
pathOrRef
reason
priority
sensitivity
includedContent
```

## Context Source 타입

MVP에서 사용하는 sourceType은 다음으로 제한한다.

| sourceType | 설명 | 예시 |
| --- | --- | --- |
| `JIRA_TASK` | 작업 목적, 범위, 완료 기준 | `IT-000` |
| `DESIGN_DECISION` | 제품/아키텍처 결정 | `harness/DESIGN_CONTEXT.md` |
| `CODEBASE` | 관련 소스 코드 | `src/main/java/...` |
| `TEST` | 관련 테스트와 검증 명령 | `src/test/java/...`, `./gradlew test` |
| `API_CONTRACT` | API 요청/응답, 상태 코드 | 설계 문서 또는 컨트롤러 |
| `DB_SCHEMA` | 테이블, 컬럼, 관계 | migration, entity, schema 문서 |
| `TEAM_CONVENTION` | 브랜치, 커밋, 코드 스타일 | 팀 컨벤션, `.github` 템플릿 |
| `SECURITY_POLICY` | 민감정보, 권한, workdir 보안 | 보안 설계, `.gitignore` |
| `EXECUTION_POLICY` | Agent 실행 권한과 승인 기준 | `harness/EXECUTION_POLICY.md` |
| `TOOL_REGISTRY` | 도구 사용 권한과 실패 응답 | `harness/TOOL_REGISTRY.md` |
| `VERIFICATION_GATE` | 테스트, 린터, CI 검증 기준 | `harness/VERIFICATION_GATE.md` |
| `TRACE_POLICY` | 실행 로그, 재현, 이상 행동 추적 기준 | `harness/TRACEABILITY.md` |
| `PR_TEMPLATE` | PR 제목/본문 규칙 | `.github/PULL_REQUEST_TEMPLATE.md` |

## 우선순위 정책

컨텍스트는 높은 우선순위부터 주입한다.

### P0 필수

항상 포함한다.

- Jira Task 키, 제목, 작업 범위, 완료 기준
- 현재 브랜치, 기준 브랜치, 작업 트리 상태
- 사용자 승인 게이트
- 민감정보 금지 규칙
- 현재 작업과 직접 관련된 파일 목록

### P1 높음

작업 결과에 직접 영향을 주면 포함한다.

- 관련 설계 결정
- 관련 코드와 테스트
- API 계약
- DB 스키마
- 보안 정책
- 팀 컨벤션
- 실행 정책
- 도구 권한
- 검증 게이트

### P2 보통

판단 보조용으로 필요할 때만 포함한다.

- 로드맵
- 후순위 확장 계획
- 운영 고려사항
- 이전 유사 작업 기록

### P3 낮음

기본적으로 제외하고, 사용자가 요청할 때만 포함한다.

- 작업과 직접 관련 없는 긴 문서 전문
- UI/운영/외부 연동 문서의 전체 내용
- 완료된 논의의 세부 대화

## 크기 제한 정책

MVP에서는 정확한 token counter를 구현하지 않는다.
대신 다음 규칙으로 크기를 제한한다.

- 긴 문서는 전문 대신 핵심 결정과 파일 경로만 포함한다.
- 코드 파일은 필요한 함수, 클래스, 테스트 주변만 읽는다.
- `docs/design`처럼 큰 문서 묶음은 필요한 섹션만 요약한다.
- 같은 내용이 여러 문서에 있으면 가장 권위 있는 문서 하나만 사용한다.
- 컨텍스트가 과도하면 먼저 사용자에게 범위를 좁혀야 한다고 알린다.

권장 상한:

```text
작은 수정: 3~5개 source
일반 기능 작업: 5~10개 source
도메인/설계 변경: 10~15개 source
```

## 제외 기준

다음은 Context Pack에 넣지 않는다.

- API key
- PAT
- Slack token
- Gateway token
- private key
- credential helper 내용
- `.env`
- `.git/config`
- 사용자가 요청하지 않은 ignored 로컬 문서 전문

## Task별 조립 규칙

### 문서 작업

필수:

- `JIRA_TASK`
- 관련 설계 결정
- 팀 컨벤션
- PR 템플릿

검증:

- Markdown 문장 확인
- 파일 경로 확인
- `git status` 확인
- `git diff --check`

### 코드 변경 작업

필수:

- `JIRA_TASK`
- 관련 코드
- 관련 테스트
- 팀 코드 컨벤션
- 보안 정책
- 실행 정책
- 도구 권한

검증:

- 가장 가까운 타깃 테스트
- 필요한 Checkstyle 또는 Spotless 검증
- 실행하지 못한 검증의 이유 기록
- 검증 실패 시 재작업 가능한 실패 요약 기록

### DB/API 작업

필수:

- `JIRA_TASK`
- API 계약
- DB 스키마
- Entity/Repository/Service/Controller 주변 코드
- 관련 통합 테스트

검증:

- API 계층 테스트
- DB 제약과 migration 영향 확인
- 응답 envelope과 에러 코드 확인

### PR 생성 작업

필수:

- `JIRA_TASK`
- branch 상태
- changed files
- verification result
- `.github/PULL_REQUEST_TEMPLATE.md`
- `harness/PR_GUIDE.md`

검증:

- base branch가 `develop`인지 확인
- PR 제목에 Jira 키와 타입이 있는지 확인
- PR 본문이 템플릿 섹션을 유지하는지 확인

## 추적 기준

작업 완료 요약에는 Context Pack 추적 정보를 남긴다.

형식:

```text
Context used:
- JIRA_TASK: IT-000
- DESIGN_DECISION: harness/DESIGN_CONTEXT.md
- TEAM_CONVENTION: harness/PR_GUIDE.md
- PR_TEMPLATE: .github/PULL_REQUEST_TEMPLATE.md

Excluded:
- docs/design full text: 필요한 섹션만 요약
- secrets: 주입 금지
```

TaskExecution을 DB로 구현할 때는 다음 필드를 고려한다.

```text
context_pack_summary
context_source_refs
context_pack_hash
context_excluded_reason
```

MVP 문서 단계에서는 DB 필드를 만들지 않는다.
대신 작업 요약과 PR 본문에 사용한 컨텍스트를 사람이 확인 가능하게 남긴다.

## 작업별 작성 템플릿

각 작업에서 사용한 Context Pack은 다음 형식으로 요약한다.

```text
taskKey: IT-000
taskSummary: [TYPE] 작업 제목
taskType: DOCUMENTATION | CODE_CHANGE | TEST | REVIEW
agentRole: BACKEND | FRONTEND | QA | DOCS | PM
branchName: name_type/IT-000
baseBranch: develop
```

주입한 핵심 컨텍스트를 함께 남긴다.

```text
Context used:
- JIRA_TASK: IT-000
- DESIGN_DECISION: 관련 설계 문서 또는 harness/DESIGN_CONTEXT.md
- CODEBASE: 관련 코드 경로
- TEST: 관련 테스트 경로 또는 검증 명령
- TEAM_CONVENTION: 관련 팀 규칙

Excluded:
- secrets: 주입 금지
- unrelated large docs: 필요한 섹션만 요약
```
