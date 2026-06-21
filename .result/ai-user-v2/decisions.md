# AI-User v2 — 의사결정 로그

> 형식: `## V2-D##` — 번호 순. 역참조: v1 decisions는 `.result/ai-user/decisions.md`.

---

## V2-D01 (2026-06-21) — v2 스코프 확정

**배경**: v1 종료(D-106 Best-of-N 역효과 확정, D-107 closeout, D-108 COLLECT-only). 사용자 3-미스매치 진단(`DIAGNOSIS.md`)을 바탕으로 v2 스코프를 확정함.

**결정**:

**a) 목표 bar = 계정·캐주얼**
- "글 1개"(×) → "계정 타임라인 전체를 무심한 독자가 봇으로 안 보는 수준"(○)
- 봇헌터 대상(분석상 생성품질 문제 아님) = 목표 외

**b) NATEPAN 전용 — 갈등 사연 최대 크롤 강조**
- 다른 커뮤니티(THEQOO/CLIEN) 금지 (변수 고정, lesson 3)
- NATEPAN 선택 이유: 갈등·관계 사연 게시판 = AI 페르소나 장르와 일치. v1 phase-2 교란(장르 불일치) 제거. 최심 clean 코퍼스(human 2589)
- **사용자 강조**: 갈등 사연을 계획보다 더 많이, 최대한 크롤링

**c) QLoRA = 데이터 게이트 뒤로 연기**
- 코드-only 계정 레버(메모리·trajectory·cadence) 먼저
- QLoRA 발동 조건: NATEPAN clean verified-real ≥ 수천(오너 확정 임계) AND Phase 3~5 plateau
- 발동 전까지 생성 100% Claude 프롬프트

**d) v1 제약 전면 승계 (V2 R7)**
- `AI_USER_ML_ENABLED=false` 영구
- D-108 COLLECT-only (example_bank 누적 계속)
- 출하 레버 보존: injectTypos T1~T8, cleanupTheqoo 12변환, CASUAL 25%
- `ActionExecutor.java:427` · `AiUserMlClient.java:174` 미변경

**e) kill criterion = Phase 0에서 오너 사전 등록** (임계값 TBD — Phase 0 완료 전 확정 필요)

**v1 교차참조**: D-106(Best-of-N FAIL) · D-107(closeout 전환) · D-108(COLLECT-only A 선택)

---

---

## V2-D02 (2026-06-21) — Kill Criterion 사전 등록

**결정**: 캐주얼 독자(≥3인) 계정 블라인드에서 **봇 정확 식별률 ≤ 60%** = PASS

| 조건 | 결과 |
|---|---|
| 식별률 ≤ 60% | ✅ PASS → NATEPAN 계정 레버 prod 출하 |
| 식별률 > 60% | ❌ FAIL → QLoRA 데이터게이트 평가 또는 품질-피벗 |

**근거**: v1 phase-2 동일 임계값(≤60%, 우연 50% 근처). cond5 임계와 동일 선상. 캐주얼 bar에 적합.

**등록 시각**: Phase 0 완료 시점(2026-06-21) — 측정 **전** 사전 등록 타임스탬프 확보.

*다음 결정: V2-D03 (Phase 1 크롤 설정 확정 — 크롤 대상 범위·cap·backfill 방식)*
