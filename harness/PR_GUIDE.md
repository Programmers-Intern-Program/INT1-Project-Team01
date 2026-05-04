# Pull Request Guide

## 목적

이 문서는 에이전트가 PR 생성 전 반드시 확인해야 하는 규칙을 정의한다.
PR 본문은 저장소의 `.github/PULL_REQUEST_TEMPLATE.md` 구조를 그대로 사용한다.

## PR 생성 전 체크리스트

에이전트는 사용자 승인 전에는 PR을 생성하지 않는다.

PR 생성 전 확인한다.

- Jira Task가 존재하고 담당자가 지정되어 있는가?
- 작업 브랜치가 Jira 키를 포함하는가?
- base 브랜치가 `develop`인가?
- 로컬 `develop`이 `origin/develop`과 동기화되어 있는가?
- 작업 브랜치가 최신 `develop` 위로 rebase되어 있는가?
- 변경사항이 현재 Jira Task 범위 안에 있는가?
- 검증 결과를 설명할 수 있는가?
- `.github/PULL_REQUEST_TEMPLATE.md` 내용을 유지했는가?
- secret, token, credential이 diff에 포함되지 않았는가?

## 브랜치 규칙

팀 작업 브랜치 형식은 다음을 따른다.

```text
<본인이름_타입/IT-티켓번호>
```

예:

```text
dongbin_docs/IT-123
dongbin_feat/IT-123
dongbin_setting/IT-123
```

허용 타입:

```text
feat, refactor, fix, chore, docs, test, setting, del, design
```

## Push 전 동기화 절차

브랜치를 원격에 push하기 전에는 `develop` 기준 최신 상태인지 확인한다.

기본 순서:

```bash
git checkout develop
git pull origin develop
git checkout <work-branch>
git rebase develop
```

rebase 후 확인한다.

```bash
git status
git log --oneline develop..HEAD
```

원칙:

- rebase 충돌이 나면 충돌 파일과 해결 방향을 먼저 보고한다.
- rebase 후에는 변경 범위에 맞는 검증을 다시 실행한다.
- 작업 브랜치가 이미 원격에 push된 뒤 rebase했다면, 재push에는 `--force-with-lease`가 필요할 수 있다.
- `--force` 또는 `--force-with-lease` push는 사용자 승인 없이 실행하지 않는다.

## PR 제목 규칙

PR 제목은 다음 형식을 따른다.

```text
<Jira 티켓번호> [타입] - 설명
```

예:

```text
IT-123 [DOCS] - 에이전트 하네스 규칙 문서 추가
```

허용 타입:

```text
FEAT, REFACTOR, FIX, CHORE, DOCS, TEST, SETTING, DEL, DESIGN
```

## PR 본문 템플릿

PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`의 섹션을 삭제하지 않는다.

필수 섹션:

- 작업 내용
- 관련 이슈
- 변경 사항
- 테스트 내용
- 참고 사항

Jira 기반 작업일 때 `관련 이슈`에는 Jira 키를 함께 적는다.
GitHub Issue 번호가 없으면 `close #`를 임의 번호로 채우지 않는다.

예:

```markdown
## 🔗 관련 이슈
- Jira: IT-123
```

## 문서 변경 PR 작성 기준

문서만 변경한 PR에서는 다음처럼 작성한다.

```markdown
## 📌 작업 내용
- 하네스 엔지니어링 작업을 위한 에이전트 작업 규칙 문서를 추가했습니다.
- 설계 문서 기반 컨텍스트 인젝션 기준과 PR 템플릿 준수 규칙을 정리했습니다.

## 🔗 관련 이슈
- Jira: IT-123

## 🛠 변경 사항
- [ ] 기능 구현
- [ ] 버그 수정
- [ ] 리팩토링
- [x] 문서 수정

## 🧪 테스트 내용
- [x] 로컬 테스트 완료
- [ ] Postman / Swagger 테스트
- [ ] 테스트 코드 작성 (선택)

## 📎 참고 사항
- 문서 변경이라 애플리케이션 테스트는 실행하지 않았습니다.
- `.github/PULL_REQUEST_TEMPLATE.md` 구조를 유지했습니다.
```

`로컬 테스트 완료`는 문서 변경의 경우 다음 확인으로 대체할 수 있다.

- 변경 파일 경로 확인
- Markdown 문장 확인
- `git status`로 의도하지 않은 파일 포함 여부 확인

## PR 생성 중단 조건

다음 상황에서는 PR을 생성하지 않는다.

- 사용자가 PR 생성을 승인하지 않은 경우
- 작업 범위 밖 파일이 섞인 경우
- 최신 `develop` 기준 rebase가 되지 않은 경우
- secret, token, credential이 diff에 포함된 경우
- 템플릿 섹션을 삭제해야만 설명이 가능한 경우

## PR 전 보고 형식

사용자에게 PR 승인을 요청하기 전 다음을 보고한다.

```text
Jira: IT-123
Branch: dongbin_feat/IT-123
Changed files:
- harness/README.md
- harness/CONTEXT_PACK.md
- harness/DESIGN_CONTEXT.md
- harness/PR_GUIDE.md

Verification:
- develop 동기화 및 작업 브랜치 rebase 확인
- 문서 경로 확인
- Markdown 내용 확인
- git status 확인

PR title:
IT-123 [DOCS] - 에이전트 하네스 규칙 문서 추가
```
