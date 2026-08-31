---
title: L2 Containers — ai-user
last_updated: 2026-08-31
---

# L2 Containers — ai-user

런타임 compose: `env/docker-compose.ai-user.yml`.

| 서비스 | 코드 위치 | 포트 | 호스트 노출 | 역할 |
|---|---|---|---|---|
| orchestrator (prod) | `ai-user/orchestrator/` | 8096 | 없음 | PLAN·홀딩·발행 |
| orchestrator-dev | `ai-user/orchestrator/` | 8096 | 없음 | dev DB |
| llm-ai-user | `ai-user/llm/` | 8092 | 없음 | CLI 브릿지 |
| ai-learning | `ai-user/learning/` | 8099 | localhost:8099 | crawl·example bank |
| prod-dev-sync | `ai-user/sync/` | 없음 | 없음 | prod→dev 비식별 |
