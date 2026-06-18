@CLAUDE.md

# AGENTS.md

Codex/OpenAI 계열 에이전트용 경량 엔트리 파일.

- 작업 규칙, 라우팅, 절대 규칙의 권위본은 루트 [`CLAUDE.md`](./CLAUDE.md) 하나다.
- 이 파일은 중복 복사본을 두지 않고 `CLAUDE.md`를 참조만 한다.
- Claude 기반 인증/환경값도 동일하게 사용한다: 호스트 `~/.claude`, `CLAUDE_HOST_CONFIG_DIR`, `CLAUDE_BIN`, 모델/배포 변수는 `docs/env/environment-variables.md`를 따른다.
- 세부 규칙은 `CLAUDE.md`가 참조하는 `.claude/rules/*.md`와 `docs/` 진입 문서를 따른다.
- 문서 충돌 시 우선순위는 `코드(runtime) > docs/_index.md authority > CLAUDE.md > AGENTS.md`다.
