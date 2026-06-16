# Step 9 완료 기록 — AI negative 백필 (토큰비용 0)

**날짜**: 2026-06-16  
**세션**: 7  
**상태**: ✅ 완료 — 첫 실제 AUC 확보, MAUVE 계기판 ON

---

## 문제

Step 8에서 `AI_USER_ML_COLLECT=true`를 열었지만, `pushNegative()`는 앞으로 새 글만 수집 → n_ai=0 유지. 하루 기다릴 수 없어, **기존 봇 작성 콘텐츠 백필** 방식으로 즉시 해결.

## 해결책

기존 bot-authored 글/댓글/대댓글 5803행(dev DB)을 `/corpus/ingest`에 `label=ai`로 push.  
LLM 재생성 없음 — DB의 기존 텍스트 재사용. 토큰비용 **0**.

---

## 백필 스크립트

**위치**: `.result/ai-user/scripts/backfill_ai_negatives.py`

### 핵심 SQL (dev — SELECT only)

```sql
-- 글(POST)
SELECT JSON_OBJECT(
  'community',  JSON_UNQUOTE(JSON_EXTRACT(pe.voice_profile,'$.voice_type')),
  'contentType','POST',
  'text',       COALESCE(p.body_published, p.body_raw),
  'label',      'ai',
  'source',     'BACKFILL_SELF_GENERATED')
FROM posts p
JOIN users    u  ON p.author_id = u.id AND u.synthetic = 1
JOIN personas pe ON pe.id = p.author_id
WHERE p.deleted_at IS NULL
  AND COALESCE(p.body_published, p.body_raw) <> ''
  AND JSON_UNQUOTE(JSON_EXTRACT(pe.voice_profile,'$.voice_type'))
      IN ('NATEPAN','BLIND','DCINSIDE',...);

-- 댓글+대댓글(COMMENT): 동일 패턴, post_comments.body, contentType='COMMENT'
```

### 안전 4중 필터

| 필터 | 구현 |
|---|---|
| ① 봇 전용 | `users.synthetic = 1` (SQL) |
| ② voice_type 화이트리스트 | SQL `IN (12 valid)` + Python 이중 확인 |
| ③ soft-delete 배제 | `deleted_at IS NULL` (SQL) |
| ④ 오류·거절 시그니처 | `LlmErrorSignature` 미러(40+ 패턴) — 2개 감지됨 (실제 오류 텍스트) |

### 실행 결과

```
총 조회:        5806행 (글 374 + 댓글+대댓글 5432)
시그니처 skip:  3행 (실제 거절/오류 문자열 — 정상 차단)
voice_type skip:0행
→ 푸시 요청:    5803행 (분산 배치 500개씩)
```

감지된 오류 샘플:
- `"I appreciate you testing my consistency, but I need to be direct: I'm not going..."` (ARCALIVE)
- `"저는 이 요청을 도와드릴 수 없습니다\\n이 프롬프트는..."` (MLBPARK)
- `"I understand this is asking me to role-play as a Korean user..."` (INVEN)

---

## n_ai 변화 (백필 후)

| 커뮤니티 | 이전 | 이후 |
|---|---|---|
| CLIEN | 0 | **323** |
| DCINSIDE | 0 | **143** |
| NATEPAN | 0 | **295** |
| THEQOO | 0 | **423** |
| ARCALIVE | 0 | 630 |
| FMKOREA | 0 | 902 |
| ... | 0 | ... |

---

## 첫 실제 AUC (수동 /train 트리거 직후)

job_id: `01KV6XZA5F41T9DDNK42C539BE` (DONE, ~40초)

| 커뮤니티 | 이전 AUC (synthetic) | **실제 AUC** | n_ai(학습) | n_human(학습) | content_type |
|---|---|---|---|---|---|
| CLIEN | 0.304 | **0.989** | 39 | 286 | POST |
| DCINSIDE | 0.429 | **1.000** | 20 | 39 | POST |
| NATEPAN | 0.319 | **0.562** | 5* | 443 | POST |
| THEQOO | 0.200 | **0.980** | 65 | 332 | POST |

> *NATEPAN `n_ai=5`: 학습이 `content_type=POST`만 사용하는데, dev DB에 NATEPAN 봇 글이 없음 → 5개는 이전 synthetic 샘플. NATEPAN의 295 corpus 항목은 댓글만이라 eval/readiness 카운트엔 반영되나 학습엔 미사용.

**readiness 결과**: `ready_count=4/4` — 모든 핵심 커뮤니티 `ready=true` (AUC≥0.55 + n_ai≥30)

---

## MAUVE eval (실제 AI 샘플 기반 첫 계기판)

job_id: `01KV6Y25MG6HKZHHX32N4D6GVS` (DONE, ~100초)

| 커뮤니티 | n_ai(eval) | MAUVE | ending_js_div | 해석 |
|---|---|---|---|---|
| DCINSIDE | 20 | **0.9999** | 0.526 | AI가 이미 인간과 극히 유사 |
| CLIEN | 39 | **0.9698** | 0.690 | 매우 유사, 개선 여지 작음 |
| THEQOO | 65 | **0.345** | 0.553 | 분포 차이 큼 — TSD 개선 효과 기대 |
| NATEPAN | 0 | null | null | AI POST 없어 측정 불가(댓글만) |

**MAUVE 해석 기준**: 1.0=완전히 동일 분포, 0.0=완전히 다름. THEQOO 0.345는 개선 여지가 가장 큰 커뮤니티.

---

## 중요 발견: train은 content_type=POST만 사용

학습 파이프라인이 `content_type` 필터 → **POST만 학습·eval에 사용**.  
→ COMMENT/REPLY 백필은 retrain trigger(n_ai 임계치)에는 기여하지만, 실제 모델 개선에는 POST 데이터가 필요.  
→ NATEPAN의 MAUVE/AUC 개선 = prod 봇이 NATEPAN voice_type으로 글을 더 작성해야.

---

## prod DB 미포함 이유

prod MariaDB `docker exec`가 auto-mode로 차단됨.  
prod 데이터 추가 백필 필요 시:

```bash
# prod DB 비밀번호를 .env.prod에서 확인 후:
docker exec againspring-mariadb-prod \
  mariadb -uagainspring -p<PROD_PASSWORD> againspring_prod \
  -N -B -e "<SQL>" > /tmp/prod_rows.jsonl
# 그 후 Python 스크립트로 push
```

또는 스크립트 수정: `DB_CONFIGS['prod']['password'] = '<실제비밀번호>'` + `--env prod` 실행.

---

## 다음 조치 (ready_count=4/4 달성)

1. **`AI_USER_ML_ENABLED=true` 활성화 검토** — 조건 충족(AUC≥0.55). 단, NATEPAN은 0.562로 마진 작음. CLIEN/DCINSIDE/THEQOO 3개 커뮤니티는 안전하게 활성화 가능.
2. prod 봇 활동 지속 → NATEPAN AI POST 자연 축적 → 다음 retrain에서 AUC 개선 기대.
3. **3순위 TSD 프롬프팅** (THEQOO MAUVE=0.345 — 가장 개선 여지 큼). 계기판이 이제 실제값이라 측정 기반 개발 가능.

---

## 롤백 (필요시)

```sql
-- WSL corpus DB에서 백필 전용 항목 제거:
DELETE FROM corpus_item WHERE source = 'BACKFILL_SELF_GENERATED';
-- 이후 /train 재실행
```
