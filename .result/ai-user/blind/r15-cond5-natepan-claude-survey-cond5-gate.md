# cond5 자동 게이트 — NATEPAN r15-cond5-natepan-claude-survey
> 생성: 2026-06-20T14:13:19.507928
> 방법: 보정형 proxy upper-bound + veto 복합 (D-101)
> `AI_USER_ML_ENABLED` 변경 금지 — 활성화는 사람 수동

## 판정: PROXY-FAIL

| 지표 | 값 | 기준 | 결과 |
|---|---|---|---|
| estimated_human_detection_upper_bound | 0.840 | ≤ 0.60 | ❌ |
| proxy_accuracy | 0.300 | 참고값 | — |
| tell_top_score | 2 | ≤ 3 | ✅ |
| rep_pairs | 0 | == 0 | ✅ |
| topic_overlap | 0 | == 0 | ✅ |
| confused_frac | 0.100 | ≤ 0.15 | ✅ |

## 캐비엇
proxy 파생·cross-era(Codex→Claude)·Claude 컨텐츠 미검증. PROXY-INCONCLUSIVE는 사람 PASS 아님. stateless LLM은 주제 과사용·출처 정보비대칭 감지 불가(미검증 unrecoverable gap).

## 실패 사유

- human_est_upper=0.840 > 0.60
