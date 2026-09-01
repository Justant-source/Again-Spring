# AI 스킬 운용 규칙 — 다시봄

> 이 파일은 AGENTS.md에서 `@import`된다. superpowers 등 AI 스킬을 이 프로젝트에서
> 언제 강제하고 언제 생략할지, 외부 스킬 스택을 왜 도입하지 않는지를 고정한다.

---

## 1. 스킬 발동 3단 트리거표

superpowers 기본값은 "1%라도 관련되면 무조건 스킬 호출"이다. 그대로 두면 사소한
수정에도 풀 루프가 붙어 토큰이 샌다. **이 프로젝트에서 실제로 사고가 났던 지점만
강제**하고 나머지는 상황에 맡긴다.

### 항상 강제

| 스킬 | 발동 시점 | 근거 |
|---|---|---|
| `superpowers:verification-before-completion` | "됐다/고쳤다/통과했다" 주장 전 | 이 프로젝트의 반복 사고가 전부 "테스트는 통과했는데 배포 후 발각" 유형이었다 |
| `superpowers:systematic-debugging` | 버그·장애·테스트 실패 시 | 추측 수정으로 2회 헛배포한 전례가 있다(카카오톡 인앱 버그, `feedback_reproduce_env_bugs`) |

### 조건부

- `superpowers:brainstorming` + `superpowers:writing-plans` — **신규 기능·아키텍처 변경일 때만.** 버그픽스·문서 수정·설정 변경에는 걸지 않는다.
- `superpowers:test-driven-development` — `backend/` `frontend/` 코드 변경 시.

### 명시 요청 시에만

`superpowers:using-git-worktrees`, `finishing-a-development-branch`,
`executing-plans`, `requesting-code-review`, `receiving-code-review`,
`subagent-driven-development`, `dispatching-parallel-agents`

이유: main 단일 브랜치 정책(AGENTS.md 절대 규칙 #9)과 `.claude/rules/multi-agent.md`가
이미 같은 영역(브랜치·병렬 실행)을 다룬다. 중복 강제는 걸지 않는다.

---

## 2. 외부 스킬 스택 도입 결정

### gstack — 도입하지 않는다

- 결정 레이어는 이미 보유 스킬(grill-me·decision-mapping·design-an-interface)로 충분하다.
- 배포 계열(`/canary` `/ship` `/land-and-deploy`)은 자기네 인프라를 전제해, 이 프로젝트의
  docker-compose + nginx + dev/prod 분리 구조에 맞추려면 어차피 재작성이 된다.
- 결정 스킬 1회당 10K+ 토큰 — 토큰 절감 기조와 상충.

### GSD(get-shit-done) — 스킬은 도입하지 않고 규약만 흡수한다

- `PROJECT.md`는 AGENTS.md와, `DECISIONS.md`는 `docs/shared/90-adr/`와, `KNOWLEDGE.md`는
  `.claude/rules/`와 각각 중복이다.
- 진짜 공백이던 ROADMAP/STATE만 `docs/_active/` 규약(§4)으로 흡수했다.

### 신규 스킬 추가는 상시 비용 결정으로 취급

스킬은 **실행하지 않아도 description이 매 세션 상시 로드**된다. "일단 설치하고
나중에 정리"는 금지다.

---

## 3. 로컬 스킬 위생 규칙

- `.claude/skills/`에 두는 것은 **이 프로젝트에서 실제 동작하고, 활성 플러그인과
  중복되지 않는 것**만이다.
- 2026-09-02 정리: 34개 → 15개. 제거 사유 3종:
  - 활성 플러그인(`mattpocock-skills`)과 중복 — 8개
  - `gh` CLI 미설치로 실행 불가 — 5개
  - 이 프로젝트와 무관 — 6개
- 스킬을 지울 때는 **다른 스킬이 그것을 참조하는지 먼저 확인**하고 재배선한 뒤
  지운다(라우터 `ask-matt` 포함).

---

## 4. 진행 상태 문서 규약 — `docs/_active/`

- 다단계 작업 트랙의 ROADMAP + 상태는 `docs/_active/<트랙명>.md`에 둔다.
  **git으로 추적된다.**
- `.temp/` `.result/` `.request/`에 계획·상태 문서를 두는 것은 **금지**.
  gitignore 대상이라 유실된다 — `.result/ai-user-v2/`가 실제로 소실된 전례가 있다.
- 추적되는 문서가 gitignored 경로를 권위본으로 지목하면 `lint_docs.py`의
  `no-gitignored-authority` 검사가 커밋을 막는다.
- 트랙이 끝나면 해당 모듈의 history/README로 요약 승격한 뒤 `docs/_active/`에서
  삭제한다.
