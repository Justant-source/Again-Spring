# Step 45 — R5: CLIEN POST MAUVE (R4 de-counselor 효과 측정)

## 일시
2026-06-17 (세션 19)

## 결정
D-52: R4 de-counselor 이후 신선 CLIEN POST의 MAUVE 전/후 비교 진행 중.

## 한 일
1. **M-before (전체 corpus)**: eval/baseline `01KV8KZ7H1R3Q0W0K5WAZV5DDE` 결과에서 POST 측정
   - CLIEN POST MAUVE(전체 corpus) = **0.6277** (n_human=960, n_ai=142)
   - 이전 STATE.md 기록 0.970 = 다른 corpus 상태에서 측정 (권위: 현재값 0.6277)

2. **Trigger (R4 신선분 축적)**:
   - 오케스트레이터 재배포 후 자동 틱 + 수동 트리거
   - generate-posts?voice=CLIEN&count=20 × 2회 (attempted=5, attempted=5)
   - 재배포 후 신선 CLIEN ai POST: **7건** (ingested_at > 2026-06-16 15:59:00)

3. **추가 트리거 실행 중**: count=20 × 2회 더 → ACCEPTED 대기 중

## 현재 corpus 상태 (R5 기준)
| | n_human | n_ai (전체) | n_ai (R4 신선) |
|---|---|---|---|
| CLIEN POST | 960 | 142 | 7 (→목표 ~25) |

## 핵심 지표 비교 (POST)

| 지표 | human | ai (전체) |
|---|---|---|
| spacing_error_rate | 0.6996 | 0.6300 |
| burstiness | 0.8208 | 1.1313 |
| ending_js_div | 0.6534 | (ai vs human JS분산) |
| **MAUVE** | — | **0.6277** |

**주목**: burstiness (human 0.82 < ai 1.13) → AI가 문장 길이가 더 불균일. 예상과 반대.

## 다음 (Wait → M-after)
- CLIEN 신선분 ≥25건 축적 후 신선분만 분리 MAUVE 측정
  ```sql
  SELECT text FROM corpus_item WHERE community='CLIEN' AND label='ai' AND content_type='POST' 
  AND ingested_at > '2026-06-16 15:59:00' LIMIT 50;
  ```
- 신선분만 MAUVE 계산 (Python mauve 라이브러리 직접)

## R5 M-after 결과 (세션 19, n=22)

| 측정 | 값 | 비고 |
|---|---|---|
| fresh CLIEN ai POST | 22건 | ingested_at > 2026-06-16 15:59:00 |
| human reference | 500건 | random sample |
| **MAUVE (fresh R4)** | **0.3527** | WSL mauve-text 계산 |
| MAUVE M-before (전체) | 0.6277 | n_ai=142 전체 corpus |

### 해석 주의사항
- MAUVE 0.6277 → 0.3527: **하락** = fresh R4 posts가 human과 더 다름
- 가능한 설명:
  1. 소표본 노이즈 (n=22): 신뢰 구간 넓음
  2. 주제 분포 차이: 22건 모두 소수 archetype에서 생성 (FAMILY 위주)
  3. R4 de-counselor가 예상과 다른 방향 = 상담조 제거 후 다른 AI 특성 부각
- **결론**: 방향 불확실. micro 블라인드 20쌍 필요 (사용자 참여).

### 추가 분석 (나중에)
- spacing_error_rate 비교: R4 신선분 vs human vs 구 corpus
- R4 de-counselor가 실제로 "덜 상담조"를 달성했는지 샘플 리뷰

## R5 micro 블라인드 결과 (세션 21, 2026-06-17)

| 측정 | 값 |
|---|---|
| 쌍 수 | 20쌍 (CLIEN POST corpus, random.seed(42)) |
| 사용자 정답 | 20/20 |
| **정확도** | **100%** |
| 목표 (cond5) | ≤60% |
| **cond5 판정** | **❌ FAIL** |

### 사용자 답변
```
1:B 2:A 3:A 4:A 5:B 6:B 7:B 8:A 9:A 10:A
11:A 12:B 13:A 14:A 15:B 16:B 17:A 18:B 19:B 20:A
(전부 정답)
```

### 분석 — 왜 100%인가
**주제 분포 편향(주요 인자)**: AI 생성 = 갈등 서사(연애/가족/직장) 위주. Human CLIEN = 정치·음식·주식·스포츠·잡담 등 다양. 상당수 쌍에서 "갈등 서사 = AI"로 주제만으로 판별 가능.

**문체 신호(보조 인자)**: AI의 구조적 서술("저도 비슷한 상황이었는데요... 근데..."), 균일한 길이, 오타 거의 0 — 틀림없이 판별 기여.

**두 인자 분리 불가**: 주제-매칭 human pairs(갈등 서사 vs AI 갈등 서사)로 재측정해야 순수 문체 cond5를 얻을 수 있음. 현재 100%는 "주제 + 문체 복합 탐지율".

**실용적 의미**: 실제 환경에서도 AI가 갈등 서사만 생성하면 주제로 탐지됨 → cond5 FAIL은 실질적으로 유효.

### 결론
D-53: cond5 = 100% (20/20). 목표 ≤60% 대비 40pp 초과. R9(생성 스타일 강화 라운드) 필요.

## 상태
- **M-before**: ✅ CLIEN POST MAUVE=0.6277
- **Trigger**: ✅ 22건 신선분 확보
- **M-after**: ✅ MAUVE=0.3527 (소표본 주의)
- **micro 블라인드**: ✅ 100% (20/20) — cond5 ❌ FAIL
