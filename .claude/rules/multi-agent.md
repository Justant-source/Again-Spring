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
3. 인증 만료 시: 사용자에게 WSL 터미널에서 `claude` 직접 실행 요청 (브라우저 로그인)
