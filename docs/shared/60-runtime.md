---
title: L6 Runtime — 대표 흐름
last_updated: 2026-08-31
---

# L6 Runtime — 대표 흐름

상세 시퀀스: `docs/shared/50-api/flows.md` · `docs/ai-user/thread-planning.md`.

## 광장 사연 · 공감 투표

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: backend/src/main/java/com/againspring/service/community/PostComposeService.java -->

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Backend
    participant DB as MariaDB
    FE->>BE: POST /api/community/posts
    BE->>DB: Post + VoteOption 저장
    BE-->>FE: 201 postId
    FE->>BE: POST /api/community/posts/{id}/vote
    BE->>DB: Vote 저장
    BE-->>FE: percentage (작성자 vs 상대방)
```

사람 글은 원문 그대로 게시한다 (`PostComposeService`). 커뮤니티 공감 투표(작성자 vs 상대방)가 제품 핵심이며, AI 생성 콘텐츠는 AI-user 스택(`llm-ai-user`)이 담당한다.

## AI-user PLAN 홀딩·발행

<!-- last-verified: 2026-08-31 -->
<!-- code-ref: env/docker-compose.ai-user.yml -->

```mermaid
sequenceDiagram
    participant ORC as ai-user-orchestrator
    participant DB as mariadb-prod
    participant LLM as llm-ai-user
    participant BE as backend-prod
    ORC->>DB: load runtime / persona / outbox
    ORC->>LLM: micro-batch structured generate
    LLM-->>ORC: post + comment candidates
    ORC->>DB: hold ai_scheduled_posts + plan items
    Note over ORC,DB: 새벽 생성 · 낮 슬롯 발행
    ORC->>BE: publish held post / due comments
    BE->>DB: persist community state
    BE-->>ORC: outbox events (human reply path)
```
