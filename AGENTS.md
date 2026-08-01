@CLAUDE.md

# AGENTS.md

Cursor / Codex / OpenAI 계열 에이전트용 경량 엔트리 파일.

- 작업 규칙, 라우팅, 절대 규칙의 권위본은 루트 [`CLAUDE.md`](./CLAUDE.md) 하나다.
- 이 파일은 중복 복사본을 두지 않고 `CLAUDE.md`를 참조만 한다.
- Claude 기반 인증/환경값도 동일하게 사용한다: 호스트 `~/.claude`, `CLAUDE_HOST_CONFIG_DIR`, `CLAUDE_BIN`, 모델/배포 변수는 `docs/env/environment-variables.md`를 따른다.
- **Cursor**: 프로젝트 규칙은 `.cursor/rules/*.mdc`, CLI 권한은 `.cursor/cli.json`(프로젝트) + `~/.cursor/cli-config.json`(유저). allow/deny 의도는 `.claude/settings.local.json`과 동기화한다. 유저 `approvalMode`는 `unrestricted`(= `agent --yolo` / Codex `--yolo`와 동일, deny만 차단). 인덱싱 제외는 `.cursorignore`.
- **Codex**: `.claude/settings.local.json`의 allow/deny 의도를 운영 규칙으로 따른다. 비파괴적 작업은 가능한 한 사용자 확인 없이 계속 진행하고, 파괴적 git 작업은 동일하게 금지한다. 단, Codex 플랫폼 승인/샌드박스 자체를 이 파일로 비활성화할 수는 없다.
- 세부 규칙은 `CLAUDE.md`가 참조하는 `.claude/rules/*.md`와 `docs/` 진입 문서를 따른다.
- 문서 충돌 시 우선순위는 `코드(runtime) > docs/_index.md authority > CLAUDE.md > AGENTS.md`다.
