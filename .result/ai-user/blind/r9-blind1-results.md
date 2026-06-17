# R9 blind ① 결과 — CLIEN POST (기존 코퍼스 베이스라인)

## 개요

| 항목 | 값 |
|---|---|
| 테스트 날짜 | 2026-06-17 |
| 총 쌍 수 | 20 (CLIEN POST corpus, seed=42) |
| **전체 정확도** | **100% (20/20)** |
| cond5 목표 | ≤60% |
| **cond5 판정** | **❌ FAIL** |
| 비고 | 기존 코퍼스 — Track A(injectTypos) 미적용분. R5와 동일 결과 (예상된 베이스라인). |

## 사용자 답변 vs 정답키

| 쌍 | 사용자 | 정답 | 결과 |
|---|---|---|---|
| 1 | A | A (human) | ✓ |
| 2 | B | B (human) | ✓ |
| 3 | B | B (human) | ✓ |
| 4 | B | B (human) | ✓ |
| 5 | A | A (human) | ✓ |
| 6 | A | A (human) | ✓ |
| 7 | A | A (human) | ✓ |
| 8 | B | B (human) | ✓ |
| 9 | B | B (human) | ✓ |
| 10 | B | B (human) | ✓ |
| 11 | B | B (human) | ✓ |
| 12 | A | A (human) | ✓ |
| 13 | B | B (human) | ✓ |
| 14 | B | B (human) | ✓ |
| 15 | A | A (human) | ✓ |
| 16 | A | A (human) | ✓ |
| 17 | B | B (human) | ✓ |
| 18 | A | A (human) | ✓ |
| 19 | A | A (human) | ✓ |
| 20 | B | B (human) | ✓ |

## 탐지 메커니즘 분석

> ⚠️ 이 테스트는 답변만 수집 (이유 미수집). 다음 테스트부터 이유 칸 포함.

### 주요 단서 (주제 편향)
기존 AI corpus = **갈등 서사(연애·가족·직장)** 100%. Human CLIEN = 정치·스포츠·IT·잡담 등 다양 주제.
20쌍 중 대부분에서 "갈등 = AI" 주제 단서만으로 판별 가능.

### 보조 단서 (문체 신호)
- `거든요` / `하더라고요` 반복 패턴
- 3단 서술 구조 (상황 → 감정 → 질문)
- 오타 0 (Track A 미적용 코퍼스)
- 균일한 문장 길이

### 해석

이 테스트는 **Track A 배포 전 기존 코퍼스** 기준 베이스라인 측정.
- R5 blind (2026-06-17) = 100% → 이번도 100% → **Track A 신선분 미포함 상태에서 예상된 결과**
- Track A(injectTypos)는 신선 CONFLICT 글에만 적용됨 → 기존 코퍼스 측정값 변화 없음
- **Track A 효과 측정을 위해서는 신선 CONFLICT 글 ≥10건 이후 재측정 필요**
- **cond5 핵심 레버는 Track B (CASUAL 25%)** — 주제 다양화가 돼야 탐지 단서 제거

## 히스토리 비교

| 라운드 | 커뮤 | 코퍼스 | 정확도 | 비고 |
|---|---|---|---|---|
| M5 (세션 16) | NATEPAN+THEQOO | 기존 | 82.5% (33/40) | cond5 FAIL |
| R5 (세션 21) | CLIEN | 기존 | 100% (20/20) | cond5 FAIL |
| R9 blind① (세션 22) | CLIEN | 기존 | **100% (20/20)** | cond5 FAIL (베이스라인 확인) |

## 다음 측정 조건

| 측정 | 선결 조건 | 상태 |
|---|---|---|
| Track A 신선분 blind | 신선 CONFLICT ai ≥10건 (injectTypos 적용) | 🔄 축적 중 |
| blind ② (혼합주제) | 신선 CASUAL ai ≥10건 | 🔄 축적 중 |
| R7 M-after COMMENT MAUVE | 신선 COMMENT ai ≥50건 | 🔄 축적 중 |
| POST MAUVE 재측정 | 신선 CONFLICT ai ≥40건 | 🔄 축적 중 |
