# Step 44 — P0: R3 오케스트레이터 재배포 (corpus 축적 잠금 해제)

## 일시
2026-06-17 (세션 19)

## 결정
D-50: P0 블로커 해소 — dev 오케스트레이터 재배포 후 corpus 축적 재개 확인.

## 배경
- dev 오케스트레이터 컨테이너가 R3 AS측 커밋(`96fdfdcd`, 2026-06-17 00:26:53)보다 **먼저** 빌드됨
  → 구 컨테이너의 `pushNegative`는 `source=None`을 전송
  → R3 ML 가드(`routes_corpus.py`)가 source=None을 가진 모든 'ai' ingest를 REJECTED
  → corpus 축적 완전 차단 (NATEPAN/DCINSIDE/GENERAL 자연 틱 전부 REJECTED 확인)
- R5·R6·R7 전부 "신선 봇 출력 축적" 의존 → P0 미해소 시 모두 불가

## 한 일
1. **재확인 (완료기준 ①②)**
   - 구 컨테이너 빌드: 2026-06-16 20:33:51 < R3 커밋 2026-06-17 00:26:53 → P0 블로커 확인
   - ML 가드 `routes_corpus.py` AI_SOURCE_ALLOWLIST 코드가 **커밋된 파일**에 반영 확인
   - ML 로그: NATEPAN/DCINSIDE/GENERAL source=None REJECTED 현존 확인

2. **재배포 (완료기준 ③)**
   ```bash
   cd env && docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build ai-user-orchestrator
   ```
   - 새 빌드 시각: **2026-06-17 00:59:27** > R3 커밋 00:26:53 ✅

3. **e2e 게이트 (완료기준 ④)**
   - `E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe`
   - 결과: **142 passed, 5 skipped** ✅

4. **축적 재개 확인 (완료기준 ⑤)**
   - `POST /admin/trigger/generate-posts?voice=CLIEN&count=2` → `attempted: 2`
   - ML 로그:
     - `16:01:36` — `+1 inserted, 0 skipped (dup hash), 0 filtered` (자연 틱)
     - `16:04:08` — `+1 inserted, 0 skipped (dup hash), 0 filtered`
     - `16:04:27` — `+1 inserted, 0 skipped (dup hash), 0 filtered`
   - REJECTED 완전 소멸 ✅

## Corpus 현황 (재배포 직후 DB 직접 확인)
| 커뮤니티 | label | content_type | n |
|---|---|---|---|
| CLIEN | ai | POST | 137 |
| CLIEN | human | POST | 960 |
| CLIEN | ai | COMMENT | 321 |
| CLIEN | human | COMMENT | 1023 |
| THEQOO | human | POST | 376 |
| THEQOO | ai | POST | **0** (R6 목표: ≥100) |
| NATEPAN | ai | POST | 226 |
| NATEPAN | human | POST | 388 |

## 수치
- 재배포 후 ML ACCEPTED: 3건 (REJECTED 0)
- e2e: 142 passed, 5 skipped
- 이전 REJECTED: source=None (NATEPAN, DCINSIDE, GENERAL) → 재배포 후 완전 소멸

## 함정
- corpus/stats API는 인증 필요(401) → DB 직접 쿼리로 대체
- `generate-posts attempted:N`은 "시도"이지, "게시 성공"이 아님 — ContentSafetyGuard 통과분만 실제 게시+pushNegative 호출

## 다음
- R1 DELETE: 사용자 승인 (dry-run 34 ctx_* 재확인 완료)
- R5: CLIEN 신선 POST ~30건 축적 후 MAUVE + 블라인드
- R6: THEQOO ai=0 → ≥100 축적 → 재학습
- R7: COMMENT M-before 즉시 측정 가능
