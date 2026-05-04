# Agent Execution Policy

## 목적

이 문서는 에이전트가 작업 중 수행할 수 있는 행동과 사용자 승인이 필요한 행동을 정의한다.
MVP에서는 런타임 차단 구현이 아니라, 에이전트와 리뷰어가 따를 문서 규칙으로 시작한다.

## 권한 레벨

| Level | 의미 | 예시 | 승인 |
| --- | --- | --- | --- |
| `READ` | 상태 확인과 파일 읽기 | `rg`, `sed`, `git status`, Jira 조회 | 자동 허용 |
| `WRITE` | 작업 범위 내 파일 수정 | 소스/테스트/문서 수정 | 자동 허용 |
| `VERIFY` | 로컬 검증 실행 | targeted test, Checkstyle, `git diff --check` | 자동 허용 |
| `CHANGE_HISTORY` | Git 이력 생성 또는 원격 반영 | commit, push, PR | 사용자 승인 필요 |
| `RISKY` | 복구 비용이 큰 작업 | reset, force push, 파일 대량 삭제 | 사용자 승인 필요 |
| `EXTERNAL` | 외부 네트워크/운영 리소스 접근 | dependency install, cloud change | 사용자 승인 필요 |

## 기본 허용 작업

에이전트는 Jira 작업 범위 안에서 다음을 수행할 수 있다.

- 파일 검색
- 파일 읽기
- 워크스페이스 내 파일 수정
- targeted test 실행
- 린터 또는 포맷 검증 실행
- Git 상태와 diff 확인
- Jira 이슈 조회와 작업 범위 확인

## 사용자 승인 필요 작업

다음은 사용자 승인 전 실행하지 않는다.

- 커밋 생성
- 브랜치 push
- Pull Request 생성
- `git reset`, 강제 checkout, 강제 rebase 중단 등 destructive Git 작업
- `--force` 또는 `--force-with-lease` push
- 작업 범위 밖 파일 삭제
- 의존성 설치
- 외부 네트워크 접근
- 운영 또는 클라우드 리소스 변경

## 금지 작업

다음은 명시 요청이 있어도 위험을 설명하고 재확인한다.

- secret, token, credential을 prompt, commit, PR, Jira 댓글에 포함
- `.env`, `.git/config`, private key, credential helper 내용 읽기
- 작업 범위 밖 사용자 변경 되돌리기
- broad formatting으로 무관한 파일 대량 수정
- 이유 없는 전체 파일 삭제

## 파일 접근 정책

기본 허용:

- `src/`
- `build.gradle.kts`, `settings.gradle.kts`
- `.github/`
- `harness/`
- 작업에 필요한 설정 파일

주의 필요:

- `docs/`: 사용자가 요청한 경우에만 읽는다.
- `build/`: 테스트 결과 확인 목적일 때만 읽는다.
- ignored 파일: 사용자가 명시한 경우에만 읽는다.

차단:

- `.env`
- `.env.*`
- `.git/config`
- `*.pem`
- `*.key`
- credential helper 파일

## 승인 요청 형식

승인이 필요한 작업 전에는 다음을 말한다.

```text
하려는 작업:
필요한 이유:
영향 범위:
되돌릴 수 있는 방법:
```

예:

```text
하려는 작업: 현재 작업 브랜치를 원격에 push
필요한 이유: PR 생성을 위해 원격 브랜치가 필요함
영향 범위: dongbin_feat/IT-000 브랜치
되돌릴 수 있는 방법: PR close 또는 branch 삭제
```

## 이상 행동 기준

다음 상황은 중단하고 사용자에게 보고한다.

- 동일 명령이 반복 실패한다.
- 예상보다 많은 파일이 변경된다.
- secret으로 보이는 문자열이 diff에 나타난다.
- 작업 범위 밖 파일 변경이 감지된다.
- 테스트 실패 원인이 불명확한데 계속 수정이 필요하다.
- destructive 작업 없이는 진행할 수 없다.
