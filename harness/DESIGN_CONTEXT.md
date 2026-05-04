# Design Context Injection

## 목적

이 문서는 `docs/design` 설계 문서에서 에이전트 작업에 반드시 주입해야 할 핵심 맥락만 추린다.
`docs/`는 로컬 문서로 관리될 수 있으므로, PR에 필요한 최소 설계 기준은 이 파일에 남긴다.
Context Pack 조립 규칙은 `harness/CONTEXT_PACK.md`, PR 작성 규칙은 `harness/PR_GUIDE.md`를 따른다.

## 제품 경계

에이전트는 다음 경계를 기준으로 작업한다.

```text
OpenClaw = Agent 실행 런타임
우리 서비스 = Workspace / Agent / Task / GitHub / Slack / Dashboard / 코드 화면을 관리하는 control plane
```

Spring Boot는 control plane이다.
코드 수정, commit, push, PR 생성은 Spring Boot가 직접 수행하지 않는다.
실제 작업은 OpenClaw Agent가 서버 작업 폴더에서 수행한다.

## MVP 우선순위

MVP의 핵심 검증 흐름은 다음이다.

```text
Workspace 생성
GitHub PAT / Repository 등록
Agent 생성
Skill 주입
Orchestrator 요청
Task 분해
Agent 실행
서버 작업 폴더에서 코드 작업
PR 생성 또는 결과 보고
Dashboard / 코드 화면 / Slack에서 결과 확인
```

에이전트는 작업 제안 시 이 흐름에 직접 기여하지 않는 구현을 후순위로 둔다.

후순위로 둔다.

- GitHub App
- Slack OAuth
- 복잡한 Gateway pool
- 고급 workflow builder
- 웹 터미널
- 충돌 해결 UI
- 자동 재시도 고도화
- 중앙 집중식 agent observability pipeline

## Workspace 경계

Workspace는 데이터와 권한의 경계다.

필수 규칙:

- 모든 주요 리소스는 `workspace_id` 기준으로 권한을 확인한다.
- 역할은 MVP에서 `ADMIN`, `MEMBER`만 사용한다.
- GitHub PAT, Slack 설정, Gateway 설정, Agent/Skill 변경은 ADMIN 권한을 기준으로 다룬다.
- Task 조회와 실행 결과 확인은 Workspace 멤버십 검증을 전제로 한다.

## Gateway 정책

OpenClaw Gateway는 하이브리드 구조다.

```text
MANAGED  = 서버 내부 Gateway, 기본값
EXTERNAL = Workspace ADMIN이 등록한 BYO Gateway
```

하네스 규칙:

- Agent/Task 실행 로직은 Gateway mode를 직접 분기하지 않는다.
- Spring Boot는 `WorkspaceGatewayBinding`에 연결된 Gateway를 사용한다.
- MANAGED Gateway는 외부에 직접 노출하지 않는다.
- EXTERNAL Gateway는 URL 검증과 SSRF 방어를 전제로 등록한다.
- 사용자의 LLM API Key는 우리 서비스에 저장하지 않는다.
- 우리 서비스가 저장하는 값은 Gateway URL과 Gateway Token까지로 제한한다.

## Agent 와 Skill

MVP Agent 역할은 다음을 기준으로 한다.

```text
ORCHESTRATOR
BACKEND
FRONTEND
QA
DOCS
PM
CUSTOM
```

Skill 파일 후보:

```text
IDENTITY.md
SOUL.md
AGENTS.md
TOOLS.md
USER.md
```

하네스 규칙:

- 역할별 Agent 행동은 Skill 파일로 제어한다.
- `SkillTemplate`은 역할별 기본값이고, `AgentSkillFile`은 특정 Agent에 복사된 실제 파일이다.
- DB의 `AgentSkillFile`을 source of truth로 본다.
- OpenClaw Agent file은 실행 런타임에 동기화된 사본으로 본다.
- `SYNC_FAILED` 상태 Agent는 자동 배정하지 않는다.

## Orchestrator 와 TaskExecution

핵심 분리는 다음이다.

```text
Task = 해야 할 일
TaskExecution = 실제 실행 1회
```

에이전트 실행 context에는 다음을 포함한다.

- Task 목표
- Task type
- Repository 정보
- 작업 폴더 경로
- branch naming rule
- PR 생성 정책인 `createPr`
- 테스트 명령 후보
- 보고 JSON schema
- 보안 주의사항

포함하면 안 된다.

- GitHub PAT 원문
- Slack token
- Gateway token
- credential helper 내부 값

Orchestrator는 서버가 검증 가능한 JSON으로 Task 목록을 보고해야 한다.
`createPr=true`는 MVP에서 `CODE_CHANGE` Task에만 허용한다.

## GitHub 와 PR 실행

GitHub 작업 원칙:

- GitHub 인증은 MVP에서 Workspace 단위 PAT를 사용한다.
- PAT는 암호화 저장하고 원문을 응답, 로그, 프롬프트에 노출하지 않는다.
- repository는 TaskExecution마다 새 workdir에 clone한다.
- Agent가 clone, branch 생성, 코드 수정, 테스트, commit, push, PR 생성을 수행한다.
- Spring Boot는 작업 폴더 생성, credential 준비, 로그/결과/Artifact 저장을 담당한다.

권장 작업 폴더:

```text
/data/aioffice/workspaces/{workspaceId}/executions/{taskExecutionId}/repo
```

서비스가 Agent 실행용 branch를 자동 생성할 때의 설계상 권장값:

```text
ai/workspace-{workspaceId}/task-{taskId}-{short-title}
```

이 저장소에서 사람이 작업하는 브랜치와 PR 규칙은 `harness/PR_GUIDE.md`를 우선한다.

## 보안 최소선

MVP에서도 다음은 반드시 지킨다.

- 비밀번호 해시 저장
- JWT 인증 적용
- Workspace 권한 검증
- GitHub PAT 암호화 저장
- Slack token/secret 암호화 저장
- Gateway token 암호화 저장
- 민감정보 로그 masking
- MANAGED OpenClaw Gateway 외부 미노출
- EXTERNAL Gateway URL SSRF 방어
- 파일 path traversal 방어
- 민감 파일 차단
- TaskExecution별 workdir 분리
- Slack Signing Secret 검증
- Slack event 중복 방지

## 코드 브라우저와 Workdir

파일 API와 코드 브라우저 작업은 다음을 지킨다.

- repo root 밖 접근을 차단한다.
- canonical path 검증으로 path traversal을 방어한다.
- `.git/`, `.env`, `.env.*`, `*.pem`, `*.key`, credential helper 파일은 차단한다.
- RUNNING 중 사용자 편집은 잠그거나 강한 경고를 둔다.
- 대용량 또는 바이너리 파일은 직접 표시하지 않는다.

## 로그와 Artifact

저장할 수 있는 것:

- TaskExecution 상태 변경
- Agent 실행 로그
- GitHub 작업 결과
- test result summary
- changed files
- git diff
- PR URL
- Agent final report

저장하면 안 되는 것:

- PAT 원문
- Slack Bot Token 원문
- Signing Secret 원문
- Gateway Token 원문
- credential helper 내용
- private key

## 에이전트 판단 기준

에이전트는 작업 중 다음 판단을 우선한다.

- MVP critical path에 가까운가?
- Workspace 권한 경계를 지키는가?
- 민감정보가 prompt, log, PR, Slack에 노출되지 않는가?
- Spring Boot와 Agent의 책임을 섞지 않았는가?
- PR 생성이 필요한 작업인지, 문서/분석 작업인지 구분했는가?
- 기존 팀 컨벤션을 지키는가?
