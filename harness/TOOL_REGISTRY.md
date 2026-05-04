# Tool Registry

## 목적

이 문서는 에이전트가 사용할 수 있는 도구와 승인 기준을 정의한다.
MVP에서는 실제 registry 서버를 만들지 않고, 문서 기반 권한 매트릭스로 시작한다.

## 도구 권한 매트릭스

| Tool | 목적 | 기본 권한 | 승인 필요 조건 |
| --- | --- | --- | --- |
| File read | 파일 내용 확인 | 허용 | ignored/민감 파일 읽기 |
| File write | 작업 범위 파일 수정 | 허용 | 범위 밖 파일 수정 |
| Search | 코드/문서 검색 | 허용 | ignored 문서 전체 탐색 |
| Gradle test | 테스트 실행 | 허용 | 외부 의존성 다운로드 필요 시 |
| Checkstyle/Spotless check | 스타일 검증 | 허용 | 전체 apply로 무관 파일 수정 시 |
| Git status/diff/log | 변경 상태 확인 | 허용 | 없음 |
| Git commit | 로컬 이력 생성 | 승인 필요 | 항상 |
| Git pull/rebase | develop 동기화 | 승인 필요 가능 | 충돌/dirty worktree 존재 시 |
| Git push | 원격 반영 | 승인 필요 | 항상 |
| Git force push | 원격 이력 변경 | 승인 필요 | 항상, 사유 필수 |
| GitHub PR | PR 생성/수정 | 승인 필요 | 항상 |
| Jira read | 이슈 조회 | 허용 | 없음 |
| Jira write | 이슈 생성/수정/전환 | 사용자 요청 시 허용 | 상태 전환/대량 수정 |
| Network/dependency | 외부 접근 | 승인 필요 | 항상 |

## 역할별 사용 기준

| Role | 기본 허용 도구 |
| --- | --- |
| `BACKEND` | file read/write, search, Gradle test, Checkstyle, Git status/diff |
| `FRONTEND` | file read/write, search, package script 검증, Git status/diff |
| `QA` | file read, search, test, report 작성 |
| `DOCS` | file read/write, search, markdown 확인, Git status/diff |
| `PM` | Jira read/write, 문서 작성, 상태 요약 |

모든 역할은 commit, push, PR 전에 사용자 승인을 받아야 한다.

## 도구 호출 원칙

- 작업에 필요한 최소 도구만 사용한다.
- 실패한 명령은 같은 방식으로 반복하지 않고 원인을 먼저 확인한다.
- 출력에 secret이 포함될 수 있는 명령은 실행하지 않는다.
- broad command보다 targeted command를 우선한다.
- 전체 포맷 적용은 작업 범위 밖 파일을 수정할 수 있으므로 사용자 승인 없이 실행하지 않는다.

## 표준 실패 응답

도구 호출 실패는 다음 형식으로 요약한다.

```text
tool:
command:
exitCode:
reason:
affectedFiles:
nextAction:
```

예:

```text
tool: gradle
command: ./gradlew test --tests "back.example.ExampleTest"
exitCode: 1
reason: assertion failure
affectedFiles:
- src/test/java/back/example/ExampleTest.java
nextAction: 실패 assertion 확인 후 수정
```

## Timeout / Retry 기준

MVP 기본값:

- 같은 명령은 원인 분석 없이 2회 넘게 반복하지 않는다.
- 테스트가 오래 걸리면 targeted test로 좁힌다.
- 네트워크 오류는 사용자 승인 없이는 재시도하지 않는다.
- dependency 다운로드 실패는 승인 후 재실행한다.

## 결과 기록

작업 요약에는 사용한 주요 도구와 검증 결과를 남긴다.

```text
Tools used:
- rg: 관련 파일 검색
- gradle test: targeted test 실행
- git diff --check: whitespace 검증
```
