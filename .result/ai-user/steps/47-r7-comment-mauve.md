# Step 47 — R7: COMMENT MAUVE M-before 측정

## 일시
2026-06-17 (세션 19)

## 결정
D-51: COMMENT M-before 측정 완료. AI와 human COMMENT 분포 격차 정량화.

## 한 일
- eval/baseline job `01KV8KZ7H1R3Q0W0K5WAZV5DDE` (CLIEN + NATEPAN) 실행
- 결과: POST+COMMENT 동시 측정

## R7 M-before 핵심 수치 (COMMENT)

| 지표 | CLIEN human | CLIEN ai | NATEPAN human | NATEPAN ai |
|---|---|---|---|---|
| n | 1023 | 321 | 1114 | 303 |
| **MAUVE** | — | **0.0677** | — | **0.0598** |
| spacing_error_rate | 0.5524 | 0.2928 | 0.5790 | 0.2743 |
| pos_ngram_diversity | 0.8303 | 0.8325 | 0.8363 | 0.8924 |
| burstiness | 0.8075 | 0.7585 | 1.0281 | 0.6968 |
| ending_js_div | 0.4811 | — | 0.4672 | — |
| avg_sentence_len | 8.59 | — | 8.50 | — |

## R5 부산물 (POST MAUVE — R4 de-counselor 포함 전체 corpus)

| 지표 | CLIEN | NATEPAN |
|---|---|---|
| n_human | 960 | 388 |
| n_ai | 142 | 226 |
| **MAUVE (전체 corpus)** | **0.6277** | **0.8395** |
| spacing_error_rate (human) | 0.6996 | 0.7475 |
| spacing_error_rate (ai) | 0.6300 | 0.6476 |
| burstiness (human) | 0.8208 | 0.9064 |
| burstiness (ai) | 1.1313 | 0.6156 |
| ending_js_div | 0.6534 | 0.5434 |

**주의**: CLIEN POST MAUVE 0.6277은 STATE.md 기록값 0.970과 다름. 이전 측정은 더 적은 n_ai 또는 다른 corpus 상태에서 측정된 것으로 추정. 현재값이 권위.

## 발견 — R7 개선 포인트

1. **spacing_error_rate 격차 최대** (human 0.55 vs ai 0.29): AI가 띄어쓰기 오류가 훨씬 적다.  
   → 해결: writing_quirks.spelling_level = low/medium + consistent_errors 추가 + mobile_typos=true
2. **burstiness 차이** (human 0.81 vs ai 0.76): AI가 좀 더 균일한 버스트.  
   → 해결: 길이 변동 강화 (1-2문장 초단문 ~ 5문장 혼합)
3. **ending_js_div 0.48**: 문장 종결 패턴 차이. AI는 ~습니다/~요 집중, human은 다양.  
   → 해결: 레지스터 회전 (결론 없는 마무리, 반말/줄임 혼합)
4. **COMMENT MAUVE 0.06**: 거의 최저 수준. POST(0.63)보다 10배 낮음. 댓글이 가장 큰 탐지 표면.

## 다음 (M-after 트리거)
- writing_quirks 필드 실제 프롬프트 반영 여부 확인 먼저 (ActionExecutor.appendWritingQuirks)
- 신선 댓글 축적 (자연 틱): NATEPAN/CLIEN 댓글은 자연 틱으로 생성됨
- M-after 측정: 축적 후 eval/baseline 재실행

## 상태
- **M-before**: ✅ 완료
- **M-after**: ⏳ 축적 대기 (신선 댓글 필요)
