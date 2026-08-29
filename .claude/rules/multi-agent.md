# 멀티에이전트 병렬 실행 규칙

> 이 파일은 CLAUDE.md에서 `@import`된다. 작업 효율 극대화 및 토큰 절약을 위한 규칙.

---

## 1. 로컬 머신 (메인 Claude Code 세션)

- **최대 8개 에이전트** 동시 병렬 실행
- 독립 작업(문서 작성, 코드 수정, 분석 등)은 항상 병렬 Agent() 호출로 처리
- 직렬 의존성이 없는 작업은 절대 순차 실행하지 않는다

---

## 2. WSL 서버 (100.115.252.61) — Claude Code 직접 접속

**목적**: 토큰 절약 + 속도 극대화. WSL의 20코어 CPU · RTX 3090(25.8GB VRAM) 활용.

**접속 방법**:
```bash
ssh justant@100.115.252.61 "source ~/.nvm/nvm.sh && cd ~/Data/Again-Spring-AI-User && claude -p '<작업 지시>' 2>&1"
```

또는 비대화형 배치 작업 (output-format json):
```bash
ssh justant@100.115.252.61 "source ~/.nvm/nvm.sh && cd ~/Data/Again-Spring-AI-User && claude -p '<작업>' --output-format json 2>&1"
```

**규칙**:
- **최대 16개 에이전트** 동시 병렬 (WSL 코어 20개 기준)
- WSL 작업 디렉토리: `~/Data/Again-Spring-AI-User/` (ML 서비스 코드)
- ML 서비스 URL: `http://localhost:8201` (WSL 내부), `http://100.115.252.61:8201` (외부)
- Claude Code CLI 경로: NVM 경유 (`source ~/.nvm/nvm.sh && claude`)

**설치 명령 (WSL에서 claude CLI 미설치 시)**:
```bash
ssh justant@100.115.252.61 "source ~/.nvm/nvm.sh && npm install -g @anthropic-ai/claude-code && claude --version"
```

**왜 WSL에서 직접 실행하는가**:
- 로컬 Agent 경유 SSH 작업 → 메인 컨텍스트 토큰 소비 + SSH 왕복 지연
- WSL Claude Code → 별도 컨텍스트, WSL 로컬 파일 직접 접근, 병렬 독립 실행
- ML 학습·평가·스크립트 실행이 WSL 로컬에서 완결 → 결과만 메인 세션에 보고

---

## 3. 작업 분배 원칙

| 작업 유형 | 실행 위치 | 최대 에이전트 |
|---|---|---|
| AS 레포 코드 수정 (java/ts/yml) | 로컬 | 8 |
| 문서 작성/갱신 (.result/, docs/) | 로컬 | 8 |
| ML 학습·평가·스크립트 | **WSL Claude Code** | 16 |
| run_ab_test.py, corpus 분석 | **WSL Claude Code** | 16 |
| Docker/DB 조작 | SSH Bash 직접 or WSL Claude Code | — |

---

## 4. WSL Claude Code 초기화 체크리스트

처음 또는 세션 재시작 시:
1. `ssh justant@100.115.252.61 "source ~/.nvm/nvm.sh && which claude"` → 경로 확인
2. 미설치면: `ssh justant@100.115.252.61 "source ~/.nvm/nvm.sh && npm install -g @anthropic-ai/claude-code"`
3. 인증이 거부되면 → **§5 세션 복사** (브라우저 재로그인으로는 해결되지 않는다)

---

## 5. WSL로 Claude 세션 복사 — "WSL로 claude 세션 복사해줘"

사용자가 이 요청을 하면 **되묻지 말고 바로 실행**한다:

```bash
scripts/sync-claude-creds-to-wsl.sh            # 복사 + 검증 (멱등, 백업 자동)
scripts/sync-claude-creds-to-wsl.sh --check    # 복사 없이 현재 상태만 확인
```

`AUTH_OK`(WSL CLI)와 `BRIDGE_AUTH_OK`(llm-bridge 컨테이너)가 모두 나오면 성공이다.

**왜 필요한가** — WSL 자체 계정(`subscriptionType=max`)은 토큰이 **만료되지 않았는데도**
조직 정책으로 Claude Code 접근이 차단된다:
`Your organization has disabled Claude subscription access for Claude Code`.
로컬 AS 호스트 계정(`pro`)은 정상이므로 그 `claudeAiOauth`를 옮긴다.

**동작** — 로컬 `~/.claude/.credentials.json`의 `claudeAiOauth` 키만 WSL로 병합한다.
로컬 `mcpOAuth`는 WSL과 무관해 옮기지 않는다. 기존 WSL 파일은 타임스탬프 백업 후 교체,
mode 600 유지. 토큰 값은 SSH 파이프로만 흐르고 화면·로그에 남기지 않는다.

**컨테이너 재시작 불필요** — WSL의 `again-spring-marketing-asm-1` ·
`again-spring-marketing-llm-bridge-1` · `llm-worker` 가 `~/.claude`를 **디렉토리 bind mount**로
물고 있어 즉시 반영된다. 실제 LLM 호출 주체는 **llm-bridge**(`/usr/local/bin/claude`)이고
ASM 본체 컨테이너엔 claude 바이너리가 없다.
(AS CLAUDE.md의 "세션 만료 시 컨테이너 restart"는 로컬 `againspring-llm` 얘기다.)

**주의**
- 토큰 수명이 **하루 단위**다. 로컬이 갱신하면 WSL 사본은 낡는다 → 그때 다시 실행하면 된다.
- 한 토큰을 두 머신이 공유하므로 refresh 회전 시 한쪽이 끊길 수 있고, 사용량 한도도 공유한다.
- 스크립트가 `~/.claude`의 root 소유 항목을 점검한다 — 과거 컨테이너가 root로 써서
  세션이 끊긴 전례가 있다. 경고가 뜨면 소유권을 회수할 것.
