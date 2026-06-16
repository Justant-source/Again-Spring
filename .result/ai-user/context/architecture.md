# 목표 아키텍처 — Again-Spring-AI-User

## 핵심 좌표

| 항목 | 값 |
|---|---|
| AS 호스트 (커뮤니티) | Ubuntu, Tailscale `100.81.189.92`, GPU 없음 |
| WSL 박스 (ML) | Tailscale `100.115.252.61`, RTX 3090 24GB |
| 신규 ML 서비스 포트 | **8201** |
| AS 콜백 포트 | 8090 (nginx-dev가 외부에서 여기로 받음) |
| ASM 포트 | 8200 (참조, 겹치지 않게) |
| AS learning 포트 | 8099 (참조) |

## 토큰 (dev 값)

| 방향 | 토큰 | 값 (dev) |
|---|---|---|
| AS → ML (inbound) | API_TOKEN | `aiuser-ml-api-token-dev-2026` |
| ML → AS (outbound callback) | CALLBACK_TOKEN | `aiuser-ml-callback-dev-token-2026` |

## 데이터 흐름

```
AS (100.81.189.92)                         WSL (100.115.252.61:8201)
┌─────────────────────────────┐           ┌──────────────────────────────┐
│ orchestrator (8096)         │           │ Again-Spring-AI-User         │
│  ActionExecutor             │           │  FastAPI + aiuser-ml-db      │
│   · N drafts via llm/Claude │  Bearer   │                              │
│   · POST /rerank ──────────►│──────────►│ /rerank (CPU)                │
│   · winner 선택             │◄──────────│  degraded=True until Step 4  │
│   · AI neg push ───────────►│──────────►│ /corpus/ingest               │
│                             │           │                              │
│ learning (8099)             │           │                              │
│  /examples/export ─────────►│──────────►│ (corpus pull, Step 2)        │
│                             │           │                              │
│                             │◄──callback│ /eval/baseline done          │
│  POST /internal/callback    │  Bearer   │ /train done                  │
└─────────────────────────────┘           └──────────────────────────────┘
```

## ASM 네트워킹 선례 (그대로 복제)

- AS → ML: `POST http://100.115.252.61:8201` + `Authorization: Bearer api_token`
- ML → AS callback: `POST http://100.81.189.92:8090/api/internal/aiuser-ml/callback` + `Authorization: Bearer callback_token`
- AS도 `GET http://100.115.252.61:8201/train/{job_id}` 폴링 (fallback)
- 멱등키: AS가 `Idempotency-Key: <ULID>` 헤더 전송 (Step 5에서 AS 측 구현)

## 참조 파일 (ASM 선례)

- `backend/.../marketing/AsmProperties.java` — AS 측 URL/토큰 설정
- `backend/.../marketing/MarketingJobService.java` — 잡 생성·폴링·콜백 수신
- `backend/src/main/resources/application.yml` — `asm:` 블록 (L134-141)
- `/home/justant/Data/Again-Spring-Marketing/app/config.py` — ASM 설정 패턴
- `/home/justant/Data/Again-Spring-Marketing/app/worker/callback.py` — 콜백 발송 패턴

## Step 5에서 AS에 추가할 설정 (orchestrator/application.yml)

```yaml
ai-user-ml:
  base-url: ${AI_USER_ML_BASE_URL:http://100.115.252.61:8201}
  api-token: ${AI_USER_ML_API_TOKEN:aiuser-ml-api-token-dev-2026}
  enabled: ${AI_USER_ML_ENABLED:false}
  best-of-n: ${AI_USER_ML_BEST_OF_N:4}
  request-timeout-ms: ${AI_USER_ML_TIMEOUT_MS:8000}
  apply-to: ${AI_USER_ML_APPLY_TO:POST,COMMENT}
```
