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

## 상태
- **M-before**: ✅ CLIEN POST MAUVE=0.6277
- **Trigger**: ✅ 수행 중 (신선분 7건 확인, 추가 트리거 중)
- **Wait**: ⏳ ≥25건 목표
- **M-after**: 🔜 25건 도달 후
