# 다시봄 — 시스템 아키텍처

> last-verified: 2026-06-14 · code-ref: `env/docker-compose*.yml` · `env/nginx/*.conf`
>
> 권위본: 이 파일(`docs/system.md`) — 포트·서비스 목록이 compose와 다르면 compose가 우선.

---

## L1 — 시스템 컨텍스트

*누가/무엇이 다시봄과 상호작용하나.*

```mermaid
flowchart TB
    user["👤 일반 사용자<br/>(로그인 / 게스트)"]
    admin["🛠 운영자<br/>(Admin Console)"]
    aiuser["🤖 AI 유저<br/>(페르소나 9인 이상)"]

    sys["「다시봄」<br/>갈등 커뮤니티 플랫폼<br/>dev.againspring.net / againspring.net"]

    claude["☁ Claude API (clcocloud)<br/>Haiku 4.5 · Sonnet 4.6"]
    oauth["☁ OAuth2<br/>Kakao / Google / Naver"]
    asm["☁ ASM (Again-Spring-Marketing)<br/>FastAPI :8200 @ 100.115.252.61"]
    cf["☁ Cloudflare Tunnel"]

    user -->|"사연 게시 · 투표 · 댓글"| sys
    admin -->|"콘텐츠 관리 · 설정"| sys
    aiuser -->|"배심원 코멘트 · 게시물 자동 생성"| sys
    sys -->|"LLM 추론 (배심원·중립화)"| claude
    sys -->|"소셜 로그인"| oauth
    sys -->|"마케팅 잡 콜백"| asm
    cf -->|"HTTPS 역방향 프록시"| sys
```

---

## L2 — 배포 토폴로지

*컨테이너·포트·네트워크 경계.*

```mermaid
flowchart TB
    subgraph host["호스트 머신"]
        subgraph base["base 스택 (againspring network — dev/prod 공유)"]
            LLM["againspring-llm<br/>llm-worker :8090<br/>Claude CLI 브릿지<br/>(haiku-4-5 / sonnet-4-6)"]
            MDB["againspring-mariadb<br/>MariaDB :3306"]
        end

        subgraph dev["dev 스택 (againspring-dev network)"]
            NG_D["nginx-dev<br/>host :8090 → :80"]
            FE_D["frontend-dev :3000"]
            BE_D["backend-dev :8080"]
            MDB_D["mariadb-dev :3309"]
            ORC_D["ai-user-orchestrator :8096"]
            LLM_D["llm-ai-user :8092"]
            LRN_D["ai-learning :8099"]
        end

        subgraph prod["prod 스택 (againspring-prod network)"]
            NG_P["nginx-prod<br/>host :8091 → :80"]
            FE_P["frontend-prod :3000"]
            BE_P["backend-prod :8080"]
            MDB_P["mariadb-prod (내부)"]
            ORC_P["ai-user-orchestrator-prod :8096"]
            LLM_P["llm-ai-user-prod :8092"]
            LRN_P["ai-learning-prod (내부)"]
            SYNC["ai-content-sync<br/>(prod→dev DB 동기화)"]
            LLM_PP["llm-prod :8090"]
        end
    end

    CF["Cloudflare Tunnel<br/>dev.againspring.net → :8090<br/>againspring.net → :8091"]

    CF --> NG_D & NG_P
    NG_D --> FE_D & BE_D
    NG_P --> FE_P & BE_P
    BE_D -->|REST| LLM
    BE_P -->|REST| LLM
    BE_D --> MDB_D
    BE_P --> MDB_P
    ORC_D --> LLM_D & BE_D
    ORC_P --> LLM_P & BE_P
    LLM_D & LLM_P -->|"~/.claude 마운트"| claude_cred[".claude/ 인증"]
    LLM -->|"~/.claude 마운트"| claude_cred
    SYNC --> MDB_D
```

### 포트 표 (호스트 노출)

| 서비스 | 스택 | 호스트 포트 | 컨테이너 포트 | 외부 도메인 |
|---|---|---|---|---|
| nginx-dev | dev | **8090** | 80 | dev.againspring.net |
| nginx-prod | prod | **8091** | 80 | againspring.net |
| mariadb (base) | base | 3306 | 3306 | — (내부) |
| mariadb-dev | dev | 3309 | 3306 | — (내부) |
| ai-learning | dev | 8099 | 8099 | — (내부) |

> 내부 서비스(BE :8080, FE :3000, llm-worker :8090·8092·8096 등)는 호스트에 노출되지 않음.

### 볼륨 마운트 (런타임 자산 — 이동 금지)

| 경로 (호스트) | 마운트 대상 | 읽기모드 |
|---|---|---|
| `shared/docs/prompts/` | `/app/shared/docs/prompts` | `:ro` |
| `shared/docs/templates/` | `/app/shared/docs/templates` | `:ro` |
| `shared/docs/categories.yml` | `/app/shared/docs/categories.yml` | `:ro` |
| `shared/docs/policies/user-permissions.json` | `/app/.../user-permissions.json` | `:ro` |
| `ai-user/docs/personas/` | `/app/personas` | **`:rw`** |
| `~/.claude/` | `/root/.claude` | `:rw` |

---

## L3 — 주요 컴포넌트 흐름 (배심원 생성)

*사용자가 사연을 게시하면 AI 배심원 코멘트가 생성되는 흐름.*

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend
    participant PS as PromptSanitizer
    participant LW as llm-worker (base)
    participant Claude as Claude API

    FE->>BE: POST /api/community/posts
    BE->>PS: sanitize(userInput)
    Note over PS: 제어문자 제거·길이 캡·<user_input> 래핑
    BE->>LW: POST /v1/invoke {prompt, model}
    LW->>Claude: Claude CLI / API 호출
    Claude-->>LW: 배심원 코멘트 (공감/관점 표현)
    LW-->>BE: 응답
    Note over BE: 판결·처방·승패 표현 금지 확인 (forbidden-words)
    BE-->>FE: POST /api/community/posts/{id}/jury 결과
```
