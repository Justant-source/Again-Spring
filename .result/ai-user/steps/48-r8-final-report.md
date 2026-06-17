# Step 48 — R8: 5조건 최종 현황 + cond4 분기 + R9 계획

## 일시
2026-06-17 (세션 21)

## 결정
D-54: R5~R7 완료(R7 M-after 제외) 후 6라운드 최종 현황 결산. cond5 FAIL 확정 → R9 착수 필요.

---

## 5조건 최종 현황 (2026-06-17)

```
cond1: ✅ CLIEN/NATEPAN/THEQOO n_ai≥100 달성
cond2: ✅ CLIEN/NATEPAN/THEQOO AUC 정상 (학습됨)
cond3: ✅ SPLITTER_VERIFIED=True
cond4: ✅ NATEPAN Δ=+0.1667 PASS (R1 -0.38% → 청결, A-B 동결 유지)
       ❌ THEQOO P(human) 방향 역전 → HALT (AUC=1.000이지만 신뢰불가)
       ❌ CLIEN Δ=0 (MAUVE ceiling 0.996)
cond5: ❌ FAIL — 사용자 블라인드 100% (20/20, 2026-06-17)  목표 ≤60%
```

**AI_USER_ML_ENABLED 상태**: false (불변 — 5조건 미충족)

---

## cond4 분기 결정 (D-49 기준)

| 커뮤니티 | 상태 | 근거 |
|---|---|---|
| **NATEPAN** | ✅ PASS (동결 유지) | Δ=+0.1667±0.1257. R1 -0.38% < 5% → provisional 아님. A-B 재실행 불필요 |
| **THEQOO** | ❌ HALT (P(human) 역전) | AUC=1.000이지만 슬랭=0.0009, 격식=0.98 → 방향 역전. 순환 측정 불가 |
| **CLIEN** | ❌ CEILING | MAUVE 0.996 천장. Δ=0. 생성 다양성 확대 필요 (R9) |

NATEPAN만 cond4 충족. THEQOO/CLIEN은 R9 생성 스타일 강화 후 재측정 대상.

---

## cond5 상세 (R5 블라인드)

| 항목 | 값 |
|---|---|
| 테스트 규모 | 20쌍 (CLIEN POST corpus, seed=42) |
| **사용자 정확도** | **100% (20/20)** |
| 목표 | ≤60% |
| 갭 | +40pp 초과 |

**탐지 메커니즘 분석**:
1. **주제 편향(주요)**: AI=갈등 서사(연애/가족/직장) 위주 vs Human=다양 주제(정치·음식·주식·스포츠). 주제로 구별 가능.
2. **문체 신호(보조)**: "저도 비슷한 상황이었는데요..." 도입부 패턴, 구조적 3단 서술, 오타 0, 균일 길이.

**측정 한계 참고**: 주제-매칭 human pairs(갈등 서사만)로 재측정하면 순수 문체 cond5 ↓. 그러나 실 운영 환경에서 AI가 갈등 서사만 생성하면 주제로도 탐지됨 → 현실 cond5로 유효.

---

## R7 현황 (COMMENT MAUVE — 미완)

| 항목 | 값 |
|---|---|
| M-before CLIEN | 0.0677 (n_ai=289, n_human=1215) |
| M-before NATEPAN | 0.0598 (n_ai=301, n_human=1297) |
| spacing_error_rate AI | 0.293 vs Human 0.552 (AI가 너무 깔끔) |
| Haiku 거절 픽스 | ✅ f7c477a8 (2026-06-17 09:13 재빌드) |
| M-after 신선분 | 🔄 축적 중 (픽스 후 신선분 대기) |

M-after 측정은 충분한 신선 ai COMMENT 축적 후 R7-complete로 기록 예정. R8에는 M-before 결과만 반영.

---

## 6라운드 전체 결산 (R0~R8)

| 단계 | 핵심 성과 | cond5 기여 |
|---|---|---|
| R0 | clcocloud API 우선 래퍼 | 기반 |
| R1 | ctx_* 오라벨 34건 제거 (CLIEN-32, NATEPAN-2) | 판별기 청결 |
| R2 | 인코딩 방향 D-45 확정 | 측정 신뢰성 |
| R3 | SELF_GENERATED 소스 가드 | 순환 오염 차단 |
| R4 | CLIEN de-counselor + writing_quirks 7개 | cond5 레버 1차 |
| R5 | MAUVE 0.6277→0.3527(신선22건) + 블라인드 **100%** | ❌ FAIL 확정 |
| R6 | THEQOO n_ai=100 + AUC=1.000 **P(human) 역전 HALT** | corpus 오염 탐지 |
| R7 | COMMENT MAUVE M-before=0.0677 + Haiku 픽스 | cond5 대형 표면 확보 |
| R8 | 현황 결산, R9 필요 확정 | 방향 설정 |

---

## R9 계획 (cond5 전용 라운드 — 다음 세션 착수)

### 목표
cond5: 100% → ≤60% (사용자 블라인드 기준)

### 필요 레버 (우선순위 순)

| 레버 | 현재 상태 | 필요 변경 |
|---|---|---|
| **오타 주입** | voice.yml spelling_level=low, consistent_errors=[], mobile_typos=false | mobile_typos=true, consistent_errors 1-2건 추가 |
| **주제 다양화** | 갈등 서사 위주 (AS 플랫폼 갈등 분야만) | CLIEN 봇이 다양 주제(음식·일상·잡담) POST 가능하도록 — **AS 로직 변경 필요** |
| **길이 변동** | 현재 균일 (~5~8줄) | 1~2문장 초단문 + 10줄+ 장문 혼합 |
| **레지스터 회전** | 상대적으로 격식 | 반말/존댓말·갑작스런 종결·결론 없는 마무리 |

### 우선 순서 (토큰/리스크 기준)
1. **오타 주입** (voice.yml만 수정, 저토큰) → 즉시 가능
2. **주제 다양화** (AS 게시글 카테고리/주제 로직 변경) → BE 수정 필요, e2e 게이트
3. 길이/레지스터는 프롬프트 수정으로 가능

### R9 측정 계획
- 블라인드 20쌍 (갈등주제 human vs AI — 주제 매칭 버전)
- 갈등 서사 주제 매칭 후 cond5 재측정 → 순수 문체 정확도 확인

---

## 다음 세션 우선 순위

1. **R7 M-after**: 신선 COMMENT ai ≥50건 확인 후 MAUVE 재측정 (WSL)
2. **R9 오타 주입**: voice.yml mobile_typos=true + consistent_errors 추가 (즉시 가능)
3. **주제 다양화**: CLIEN 봇의 POST 주제 다변화 (AS BE 수정 필요 — e2e 게이트)
4. R9 블라인드 20쌍 (갈등 주제 매칭) 재측정

## 상태
✅ 6라운드 R0~R8 현황 결산 완료
🔜 R9: 생성 스타일 강화 (오타 주입 + 주제 다양화) → cond5 ≤60% 재도전
