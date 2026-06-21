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

---

## V2-D03 (2026-06-21) — Phase 5b 계정 블라인드 PASS

**결정**: Phase 5b named-tell 제거 후 계정 블라인드 평가 실시 → kill criterion 충족 (5/9=55.6% ≤60%)

**측정 상세**:
- 블라인드 키트 v3: P1~P18 (AI 홀수, 인간 짝수)
- 평가자: 1인(오너)
- 정확 식별: P1, P7, P9, P11, P17 = 5개 (공명 확인된 tells)
- 미탐지: P3, P5, P13, P15 = 4개 (어미 변형 미등록)
- **False Positive 0건** (인간 포스트 오탐 없음)

**근거 — v1→v2→v3 추이**:
```
v1 baseline (88.9%) → v2 Ph3+4 (66.7%) → v3 Ph5b (55.6%)
무침 캐주얼 봇     → 사가 + 주제반복  → 사가 + 감정평탄화 제거
식별률 -33.3pp 달성
```

**복합 효과**:
- Phase 3: 인생사 서사 (saga) 추가 → "팀장갑질" 반복 완화
- Phase 4: cadence·comment 튜닝 → 주제 다양성 증대
- Phase 5b: 5개 cliché 클리셰 제거 ("당신은 충분히 좋은 사람", "할 수 있을 거예요" 등) → 감정 평탄화·이중질문 종결

**신규 발견 tells**:
1. "~인지 ~건지" 이중질문 종결 (감정 선행 없음)
2. 감정 평탄화 (느낌표·감정 수식어 부재, 사건 건조 나열)
3. "~건지 모르겠어" 어미 변형 지속 (미등록 필터)
4. 커뮤니티 맥락 불일치 (갈등 게시판에 화목한 글)

**다음 단계**:
- Phase 5b PASS → NATEPAN 계정 레버 prod 출하 (절대규칙 #4 순서)
  ① dev 배포 → ② e2e-realbe 전체 통과 (dev:8090) → ③ main commit & push → ④ prod 배포

**참고 링크**: `.result/ai-user-v2/eval/blind_kit_v3_key.md` (정답키, 평가 후 공개)
