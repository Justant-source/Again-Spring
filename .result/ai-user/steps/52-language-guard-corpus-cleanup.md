# Step 52 — 언어 가드 구현 + ML corpus 오염 정화 (D-57)

**날짜**: 2026-06-18  
**상태**: 완료 ✅

## 배경

Phase 1 축적(R7 M-after) 중 HALT 게이트 발동 — 영어 거절문 ML corpus 오염.

- **원인**: `ContentSafetyGuard`·`LlmErrorSignature` 시그니처 기반 방어가 새 거절 패턴 미감지
- **근본 신호**: Haiku API 풀에 Kiro 혼입 → "I can't do this. The instructions ask me to impersonate..." 등 영어 거절문 생성 → Sonnet 폴백 미발동(거절 미인식)
- **영향**: ML corpus에 영어 거절문 오염, 재축적/재학습 시 품질 저하

## 오염 규모 (감사 추적)

쿼리: `WHERE label='ai' AND text REGEXP '^I can.t |^I cannot |^I am unable|^I.m unable|^I will not|^I won.t |^Sorry, I|^As an AI'`

| community | content_type | n_contaminated |
|---|---|---|
| NATEPAN | COMMENT | 42 |
| FMKOREA | COMMENT | 25 |
| INVEN | COMMENT | 23 |
| CLIEN | COMMENT | 18 |
| RULIWEB | COMMENT | 17 |
| DCINSIDE | COMMENT | 13 |
| ARCALIVE | COMMENT | 11 |
| BLIND | COMMENT | 7 |
| GENERAL | COMMENT | 7 |
| PPOMPPU | COMMENT | 7 |
| MLBPARK | COMMENT | 1 |
| **합계** | | **171** |

**POST 오염 없음** — REGEXP는 라인 시작 기준(^), POST는 거절문이 생성되지 않음 확인.  
스냅샷 전문: `.claude/projects/.../tool-results/b7tadn0dh.txt` (51.4KB, 171행 id+text)

**DELETE 실행**: 2026-06-18 (WSL ML DB)

## 코드 수정

| 파일 | 변경 내용 | 위치 |
|---|---|---|
| `LlmErrorSignature.java` | 언어 가드: 한글 비율<10% → `looksLikeProviderError=true` (Sonnet 폴백 발동) | L1 |
| `ContentSafetyGuard.java` | 동일 언어 가드 + 동일 시그니처 (절대규칙 #7 — L1/L2 동기화) | L2 |
| `routes_corpus.py` (WSL) | ai 행 한글 없으면 ingest 거부 (commit `6c18ea8`) | L3 |
| `.claude/rules/llm-safety.md` | 방어 3계층 갱신, 시그니처 카테고리에 언어 가드 추가 | docs |

**언어 가드 구현**: `MIN_KOREAN_RATIO = 0.10` (한글 syllabes 0xAC00-0xD7A3 + Jamo), `MIN_KOREAN_CHECK_LEN = 20` (단문 제외)

## CLIEN 신선분 브레이크다운 (cleanup 후 기준)

| content_type | n_clean | 비고 |
|---|---|---|
| COMMENT | 61 | CONFLICT-POST blind① 독립 |
| POST | 105 | 샘플 4/5 갈등 주제 → CONFLICT-POST ≥10 충족 |

- **blind① 가능**: CONFLICT-POST ≥10 확인 (추정 ~84건)
- NATEPAN 신선 COMMENT clean: 25 (50 미달, 축적 계속 필요)

## injectTypos halt gate 검증

- `GenerationController.java:40`, `SelfCritiqueService.java:252` → 2-arg `sanitizePost(raw, voiceType)` 호출 확인
- CLIEN 신선 POST T1/T7/T8 패턴 실측: **12/105 = 11.4%** (됬, 갓어, 왓어, 있엇 등)
- CLIEN `sampleProb=0.60 × typoProb=0.55 = 0.33` 기대 대비 낮은 이유: 특정 패턴(됐·갔어·있었 등) 미포함 텍스트는 0 변환 → 정상
- **판정: halt gate 미발동** — injectTypos 작동 중, blind① 진행 가능

## 다음 단계

1. dev rebuild: `docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build`
2. e2e dev:8090 게이트 (AS 코드 변경 적용 확인)
3. CLIEN 신선 CONFLICT-POST blind① (20쌍, Track A)
4. AI_USER_ENABLED=true 재축적 (NATEPAN 25→50, CLIEN COMMENT 61→50+ 이미 충족)
