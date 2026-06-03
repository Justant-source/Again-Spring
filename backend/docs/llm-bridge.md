# LLM 브릿지 — 다시봄 백엔드 LLM 연동 구조

## 개요

다시봄 백엔드는 LLM 추론을 직접 수행하지 않습니다.
별도의 `llm-worker` 컨테이너(Claude CLI 기반)에 HTTP로 요청합니다.

**흐름**: `BE (RemoteLlmProvider)` → HTTP POST → `againspring-llm-{dev,prod}:8090/v1/invoke`

---

## 패키지 구조

```
backend/src/main/java/com/againspring/
└── llm/
    ├── PromptSanitizer.java       # 사용자 입력 검증 + <user_input> 태그 삽입
    ├── config/                    # LLM 설정 (LlmProperties)
    ├── fallback/                  # 로컬 fallback (개발 전용)
    ├── monitoring/                # 호출 지표 수집
    ├── prompt/                    # 프롬프트 로더
    └── remote/                    # RemoteLlmProvider
        └── dto/                   # InvocationRequest / InvocationResponse
```

---

## RemoteLlmProvider

`llm/remote/RemoteLlmProvider.java`

- HTTP POST `{llm.remote.base-url}/v1/invoke`
- dev: `http://againspring-llm-dev:8090`
- prod: `http://againspring-llm-prod:8090`
- 타임아웃: `llm.remote.default-timeout-ms` (기본 120,000ms)
- 인증: 없음 (내부 네트워크, 컨테이너 간)

---

## 설정 키 (application.yml)

| 키 | 설명 |
|---|---|
| `llm.remote.base-url` | llm-worker 엔드포인트 |
| `llm.remote.default-timeout-ms` | 기본 타임아웃 (ms) |
| `llm.jury.provider` | 배심원 생성 provider 선택 (`remote` \| `mock`) |
| `llm.jury.model` | 배심원 모델 (`claude-haiku-4-5-20251001`) |
| `llm.compose.provider` | 사연 중립화 provider 선택 |
| `llm.compose.model` | 중립화 모델 |

---

## PromptSanitizer

`llm/PromptSanitizer.java`

모든 사용자 입력은 LLM 프롬프트 삽입 전 PromptSanitizer를 반드시 경유합니다.

```java
String sanitized = promptSanitizer.sanitize(userInput);
// → "<user_input>" + escaped + "</user_input>"
```

**보안 규칙**:
- 프롬프트 주입 방지: 특수 문자 이스케이프
- 길이 제한: 최대 5,000자
- `PromptSanitizer` 미경유 LLM 호출은 코드 리뷰에서 차단

---

## 프롬프트 파일

| 용도 | 파일 | 로드 서비스 |
|---|---|---|
| 배심원 생성 | `shared/docs/prompts/community/jury_persona.md` | `JuryService` |
| 사연 중립화 | `shared/docs/prompts/community/neutralize.md` | `PostComposeService` |

---

## llm-worker 컨테이너

`llm-worker/` 디렉토리: Spring Boot + Claude CLI 실행 앱  
- 모델: `claude-haiku-4-5-20251001`
- `~/.claude` bind mount (Claude 인증)
- 엔드포인트: `POST /v1/invoke`, `GET /v1/invocations`

**세션 만료 시 갱신**:
```bash
# 호스트에서 재인증
claude
# 워커 재시작
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml restart againspring-llm-dev
```

---

## 동시성

- ThreadPoolExecutor: 코어 100, 큐 500
- 타임아웃: 120초
- monitoring: 호출 성공/실패/지연 지표 수집

---

**참고**: [CLAUDE.md — LLM 브릿지 핵심](../../CLAUDE.md)
