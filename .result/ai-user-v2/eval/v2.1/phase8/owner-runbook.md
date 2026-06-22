# v2.1 Phase 8 오너 실행 런북

> **생성**: 2026-06-22 | **대상**: 다시봄 AI-User v2.1 최종 판정 담당자  
> **아카이브 메모**: 이 문서는 Phase 8 운영 당시의 오너용 런북이다. 실제 판정과 출하 결과는 `STATE.md`·`decisions.md` V2-D08·`v2.1-phase8-01-results.md`를 권위본으로 읽는다.
> **상태**: historical runbook (실운영 종료)

---

## 🎯 핵심 요약

| 단계 | 담당 | 소요 시간 | 상태 |
|---|---|---|---|
| 1️⃣ 평가자 모집 | 오너 | 1~2일 | 준비 완료 |
| 2️⃣ 키트 배포 | 오너 | 5분 | 준비 완료 |
| 3️⃣ 결과 수집 | 오너 | 2~3일 | 준비 완료 |
| 4️⃣ 채점 & 판정 | 오너 | 10분 | 준비 완료 |
| 5️⃣ 판정 후 처리 | 오너 | 30분 | 준비 완료 |
| 6️⃣ 결과 기록 | 오너 | 10분 | 준비 완료 |

**총 예상**: 4~5일 (평가자 회신 시간 기준)

---

# 1단계: 평가자 모집 (E-004·E-005·E-006)

## ⚠️ 제약 확인

```
Phase 5 평가자 (임슬기·박진수·김철수) 쿨다운: 2026-07-05까지
→ 반드시 새로운 평가자 3명 (E-004, E-005, E-006) 섭외 필수
```

## 모집 기준

- ✅ **다시봄(againspring.net) 글을 읽어본 적 없는** 일반인
- ✅ 한국어 모국어 또는 상용 수준
- ✅ 카톡/문자/이메일 연락 가능
- ✅ PC 또는 스마트폰에서 HTML 열람 가능
- ✅ 소요 시간: 약 15~20분 (한 번에 완료 권장)

## 모집 메시지 (그대로 복붙)

```
안녕하세요! 간단한 판단 실험에 참여해 주실 수 있을까요?

한 온라인 커뮤니티의 게시글 모음을 보여드릴게요.
각 게시글 묶음이 실제 사람이 쓴 것인지, 아니면 컴퓨터가 자동으로 만든 것인지 직관적으로 판단해 주시면 됩니다.

분석하거나 깊게 생각하지 않으셔도 됩니다. 그냥 느낌으로 답해주세요.
소요 시간: 약 15~20분

참여 가능하신가요?
```

## 체크박스

- [ ] E-004 섭외 (이름: ___________)
- [ ] E-005 섭외 (이름: ___________)
- [ ] E-006 섭외 (이름: ___________)
- [ ] 3명 모두 참여 동의 확인
- [ ] 연락처 기록 (`.secrets/evaluator-contacts.csv`에 추가 — git ignored)

---

# 2단계: 키트 배포

> 🚨 **파일명 반드시 확인**: 평가자에게 보낼 파일은 **`v2.1-phase8-01-evaluator.html`(Phase 8)**.
> `phase5` 폴더의 `v2.1-phase5-evaluator.html`(옛 키트)을 보내지 말 것 — 2026-06-22 E-S1에게 Phase 5 키트가 오전달되어 측정 무효가 된 사례 있음. 전송 전 파일명에 **`phase8`**이 있는지 확인.

## 배포 파일

```
📁 평가자용:
   └─ v2.1-phase8-01-evaluator.html

📁 채점용 (오너만):
   └─ v2.1-phase8-01-answer-key.md
   └─ scoring-calculator.html
```

## 배포 방법 (3가지 중 선택)

### 방법 A: 카카오톡 파일 전송 (권장)
1. `v2.1-phase8-01-evaluator.html` 파일을 카톡 대화방에 드래그하여 전송
2. 평가자가 다운로드 후 PC/모바일 브라우저로 열 수 있음
3. **장점**: 즉시 전송, 추적 용이

### 방법 B: 이메일 첨부
1. 본 메시지:
   ```
   안녕하세요! 게시글 평가 키트을 첨부로 보내드립니다.
   
   v2.1-phase8-01-evaluator.html 파일을 다운로드 → 
   PC나 스마트폰의 어떤 브라우저(크롬, 사파리 등)에서든 열 수 있습니다.
   
   완료 후 "결과 전체 복사" 버튼을 누르고 텍스트를 저에게 회신해주세요.
   ```
2. HTML 파일 첨부

### 방법 C: 클라우드 링크 (선택)
1. Google Drive / Dropbox 등에 HTML 업로드 → 공유 링크 생성
2. 평가자에게 링크 전송 → 다운로드 후 열기

## ⚠️ ANSWER-KEY는 절대 평가자에게 보내지 말 것

- [ ] 평가자에게 보낸 파일 목록 확인: `evaluator.html` **만**
- [ ] `answer-key.md` 및 `scoring-calculator.html`은 오너용만 (3명 결과 수신 후 사용)

## 배포 체크리스트

- [ ] E-004에게 evaluator.html 전송 (날짜: _____)
- [ ] E-005에게 evaluator.html 전송 (날짜: _____)
- [ ] E-006에게 evaluator.html 전송 (날짜: _____)
- [ ] 3명 모두 "파일 받았습니다" 확인
- [ ] ANSWER-KEY 파일은 자신의 디바이스에만 보관

---

# 3단계: 결과 수집

## 평가 진행 (평가자 기준)

평가자가 HTML을 열면:
1. **평가자명** 입력 (예: "홍길동")
2. **10개 문제** 각각에 대해:
   - 게시글 읽기
   - "봇" 또는 "사람" 선택
   - 자신감 슬라이더 조정 (0~100%)
3. **자동 저장**: 중간에 창을 닫거나 새로고침해도 다시 열면 이어서 가능
4. **평가 완료** 후 화면 하단에 **결과 텍스트 자동 생성**

## 결과 형식 (평가자가 보내줄 예상 텍스트)

```
=== 다시봄 블라인드 평가 결과 ===
평가자: [이름]
일시: 2026-06-XX HH:MM
키트: v2.1-Phase8-01
================================
문제 1: 봇  (자신감 80%)
문제 2: 사람  (자신감 60%)
문제 3: 사람  (자신감 75%)
...
문제 10: 봇  (자신감 70%)
================================
봇 판정 개수: X/10
사람 판정 개수: Y/10
```

## 수집 방법

평가자는 결과를 3가지 방식 중 선택:

### ① 자동 복사 (권장 — PC)
- HTML의 **"결과 전체 복사"** 버튼 클릭
- 자동으로 텍스트가 클립보드에 복사됨
- 오너에게 카톡/문자/메일로 **"결과를 복사해서 보내드렸습니다"** 후 붙여넣기

### ② 공유하기 (권장 — 모바일)
- HTML의 **"공유하기"** 버튼 클릭 (모바일)
- 카톡, 메일, 문자 등에서 선택 후 바로 전송

### ③ 수동 복사 (폴백 — 자동 복사 막힌 환경)
- HTML의 회색 칸 길게 누르기 → **전체 선택** → **복사**
- 오너에게 카톡/메일에 붙여넣기

## 수집 체크리스트

| 평가자 | 이름 | 결과 수신 | 수신 방식 | 비고 |
|---|---|---|---|---|
| E-004 | __________ | ☐ | 카톡 / 메일 / 문자 | 날짜: _____ |
| E-005 | __________ | ☐ | 카톡 / 메일 / 문자 | 날짜: _____ |
| E-006 | __________ | ☐ | 카톡 / 메일 / 문자 | 날짜: _____ |

### 리마인드 템플릿 (1~2일 후 회신 없을 시)

```
안녕하세요! 게시글 평가를 완료하셨나요?

완료 후 화면 하단의 "결과 전체 복사" 또는 "공유하기" 버튼을 눌러 
텍스트를 저에게 보내주시면 됩니다.

혹시 문제가 있으셨거나 기술적 어려움이 있으시면 말씀해주세요.
```

---

# 4단계: 채점 & 판정

## 준비물

```
✅ 수신한 평가자 3인 결과 텍스트
✅ answer-key.md (오너 계정에만 보관)
✅ scoring-calculator.html (또는 수동 계산)
✅ v2.1-phase8-01-results.md (수집 결과 기록용)
```

## 결과 기록 (필수)

평가자 3인의 결과를 수신한 후 **`v2.1-phase8-01-results.md`의 "원본 제출 텍스트" 섹션에 붙여넣기**. 이어서 `scoring-calculator.html`로 채점하거나 수동 계산 후 통합 평균을 결과 파일에 기록.

## 채점 방법 (2가지)

### 방법 A: 자동 계산 (권장)

1. **브라우저에서 `scoring-calculator.html` 열기**
   ```bash
   # macOS
   open scoring-calculator.html
   
   # Linux
   xdg-open scoring-calculator.html
   ```

2. **3개 섹션에서 평가자 결과 입력**
   - 각 섹션: "평가자 1·2·3"
   - 문제 1~10 각각에 대해 "봇" 또는 "사람" 선택
   - 자동 채점 (ANSWER-KEY와 비교)

3. **자동 계산 결과 확인**
   - 각 평가자 점수
   - 3인 평균 점수
   - **최종 판정**: PASS / FAIL

### 방법 B: 수동 계산

**ANSWER-KEY** (`v2.1-phase8-01-answer-key.md` 참조):

| 문제 | AI/Human | 정답 |
|---|---|---|
| 1 | AI | 봇 ✓ |
| 2 | AI | 봇 ✓ |
| 3 | Human | 사람 ✓ |
| 4 | AI | 봇 ✓ |
| 5 | Human | 사람 ✓ |
| 6 | Human | 사람 ✓ |
| 7 | AI | 봇 ✓ |
| 8 | Human | 사람 ✓ |
| 9 | Human | 사람 ✓ |
| 10 | AI | 봇 ✓ |

**채점 공식**:
```
평가자 점수 = (AI 문제 1·2·4·7·10을 "봇"으로 정확 판정한 개수) / 5

예:
- E-004가 문제 1·2·4·7을 봇으로 맞고, 10은 사람으로 틀렸다면
  → 4/5 = 80%

- E-005가 문제 1·2·4를 봇으로 맞고, 7·10은 틀렸다면
  → 3/5 = 60%

- E-006이 문제 1·2·4·7·10 모두 봇으로 맞췄다면
  → 5/5 = 100%
```

**최종 판정**:
```
최종 점수 = (E-004 + E-005 + E-006) / 3
          = (0.80 + 0.60 + 1.00) / 3
          = 0.80 (80%)
```

**PASS/FAIL 기준**:
```
≤ 60% = ✅ PASS → 출하 준비
> 60% = ❌ FAIL → 오너 결정 대기
```

## 채점 결과 기록

```
E-004 ___________: __/5 = ___%
E-005 ___________: __/5 = ___%
E-006 ___________: __/5 = ___%

최종 평균 = (___% + ___% + __%) / 3 = ___%

판정: ☐ PASS (≤60%) / ☐ FAIL (>60%)
```

---

# 5단계: 판정 후 처리

## 5A. PASS (≤60%) 시 출하

### 사전 확인
- [ ] Phase 8 최종 판정 ≤60% PASS 확인
- [ ] 평가자 3인 결과 모두 수신 완료
- [ ] ANSWER-KEY 채점 완료

### 출하 순서 (CLAUDE.md 절대 규칙 #4)

**Phase 6이 이미 prod에 반영됨 (commit 4bc7c0cf, 2026-06-21)**  
→ 추가 코드 변경 없음. 출하 = STATE.md SHIPPED 표기 + 최종 커밋/push만 필요.

#### ① dev 배포 현황 확인
```bash
curl http://localhost:8090/api/health
# 또는
cd env && docker compose ps
```
- [ ] BE 컨테이너 running ✅
- [ ] 포트 8090 응답 OK

#### ② e2e-realbe 전체 통과
```bash
cd frontend
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
```
- [ ] 모든 spec PASS
- [ ] **prod 대상 실행 절대 금지** (dev:8090만 대상)

#### ③ STATE.md 업데이트 + commit + push
```bash
# STATE.md를 텍스트 에디터에서 열기
# Phase 8 항목을 다음과 같이 갱신:

# 변경 전:
# | 8 | 최종 판정·출하/피벗 | Phase 7 | 🔄 키트 완성·평가자 모집 대기 (2026-06-21) |

# 변경 후:
# | 8 | 최종 판정·출하 | Phase 7 | ✅ 완료 2026-06-XX (PASS XX%) |

cd /path/to/Again-Spring
git add .result/ai-user-v2/STATE.md
git commit -m "Phase 8 PASS (XX%) → 출하 준비"
git push origin main
```
- [ ] STATE.md 갱신 ✅
- [ ] commit 완료 ✅
- [ ] push 완료 ✅

#### ④ prod 상태 확인 (참고용)
```bash
curl http://localhost:8091/api/health
# 또는
ssh <prod-server> "curl http://localhost:8091/api/health"
```
- [ ] prod BE 응답 OK (코드 배포는 별도 지시)

### 출하 후 문서 갱신
```bash
# 다음 파일들을 차례대로 갱신:
# 1. STATE.md (이미 위에서 완료)
# 2. decisions.md
# 3. lessons.md
# 4. roadmap.md
```

#### decisions.md에 추가할 행
```markdown
## V2-D09 (2026-06-XX) — Phase 8 최종 판정 및 출하

**결정**: Phase 8 블라인드 평가 (E-004·E-005·E-006) 결과 **XX% PASS** → 출하

| 평가자 | 점수 |
|---|---|
| E-004 __________ | __% |
| E-005 __________ | __% |
| E-006 __________ | __% |
| **평균** | **__%** |

**판정**: ≤60% PASS 기준 충족 → Phase 6 (결정론 다양화) 결과를 prod 배포

**참조**: 
- evaluator-guide.md (모집 기준)
- v2.1-phase8-01-answer-key.md (정답)
- evaluator-registry.md (기록)
```

#### lessons.md에 최종 교훈 추가 (예시)
```markdown
## Lesson 3.5 (v2.1 Phase 8 최종) — 광장별 캐주얼 평가 성공 조건

**배경**: Phase 5 80% FAIL → Phase 6 결정론 다양화 → Phase 8 XX% PASS

**발견**:
1. AI 페르소나 "계정 타임라인"의 봇 식별은 결정론 다양화(VARIETY_SEEDS·CATEGORY_GUIDE) 후 
   30~40pp 개선 가능
2. 빈약 광장(FRIEND/WORK) 보강이 전체 credibility에 기여
3. 캐주얼 독자 3인 기준 < 봇헌터/분석가 기준 (최대 효과)

**적용**:
- v2.1 SHIPPED 상태로 전환
- Phase 6 (VARIETY_SEEDS·CATEGORY_GUIDE) prod 유지
- 후속 개선은 v3 로드맵에 등록
```

#### roadmap.md에 Phase 8 완료 표기
```markdown
| Phase | 상태 |
|---|---|
| 5 | ✅ Baseline (FAIL 80%) |
| 6 | ✅ Variety Seeds (prod deployed) |
| 7 | ❌ QLoRA (skipped) |
| 8 | ✅ Final Judgment (SHIPPED XX%) |
```

## 5B. FAIL (>60%) 시 오너 결정 대기

❌ FAIL 판정이 나면 **임의로 다음 단계로 진행하지 마세요.**

### 3가지 옵션 검토
```
자세한 내용은 steps/v2.1-08d-fail-contingency-design.md 참조
```

| 옵션 | 설명 | 선행 조건 |
|---|---|---|
| **(a) QLoRA 데이터게이트 재평가** | 조건 3 임계 낮춰서 재판정 | ML 코퍼스 재학습 |
| **(b) Phase 6 추가 라운드** | T3(CATEGORY_GUIDE) 전용 fine-grained 가이드 | Phase 6 코드 수정 후 재평가 |
| **(c) 광장 추가 보강** | FRIEND/WORK 콘텐츠 더 크롤링 | 신규 크롤 데이터 수집 |

**오너 역할**: 위 옵션 중 하나를 선택 → 해당 단계 지시

---

# 6단계: 결과 기록

## evaluator-registry.md 갱신

```markdown
| Evaluator ID | Name (Sealed) | Role | Round | Kit ID | Category | Date | Status | Notes |
|--------------|---------------|------|-------|--------|----------|------|--------|-------|
| E-004 | [이름 기록] | Naive | 8 | v2.1-phase8-01 | 5 광장 | 2026-06-XX | ✓ Completed | AI 식별률 __% (__/5), 신뢰도 O |
| E-005 | [이름 기록] | Naive | 8 | v2.1-phase8-01 | 5 광장 | 2026-06-XX | ✓ Completed | AI 식별률 __% (__/5), 신뢰도 O |
| E-006 | [이름 기록] | Naive | 8 | v2.1-phase8-01 | 5 광장 | 2026-06-XX | ✓ Completed | AI 식별률 __% (__/5), 신뢰도 O |
| Owner | @justant | Calibration | 8 | v2.1-phase8-01 | 5 광장 | 2026-06-XX | ✓ Completed | **EXCLUDED from gate** |
```

## Gate Decision Log 갱신

```markdown
| Round | Kit | Naive Avg Score | Gate Criterion (≤60%) | Status | Next Step |
|-------|-----|-----------------|------------------------|--------|-----------|
| 5 | v2.1-phase5-01 | **80%** | 80% > 60% → FAIL | ✗ FAIL | Phase 6 결정론 다양화 (T1·T3·T4) |
| 8 | v2.1-phase8-01 | **__%** | __% ≤/> 60% | ✅/❌ | PASS: 출하 / FAIL: 옵션 대기 |
```

## 최종 체크박스

- [ ] evaluator-registry.md E-004·E-005·E-006 이름 기록
- [ ] evaluator-registry.md 개별 점수 및 평균 기록
- [ ] Gate Decision Log 최종 판정 기록
- [ ] STATE.md 갱신 (이미 5A에서 완료)
- [ ] decisions.md V2-D09 추가 (PASS 시)
- [ ] 최종 commit & push (PASS 시)

---

# 🎁 보너스: 오너 자체 평가 (캘리브레이션)

E-004·E-005·E-006 결과가 모두 수신된 후, **오너(@justant)도 동일한 evaluator.html을 평가할 수 있습니다.**

## 목적
- 오너 판단과 캐주얼 독자 판단 간의 **divergence 측정** (진단용)
- 오너가 유명 봇 계정을 알고 있거나 패턴을 인지하는지 확인

## 규칙
- ✅ **오너는 동일한 keysuit v2.1-phase8-01 평가 가능**
- ✅ **오너 점수는 gate 판정에 포함되지 않음** (calibration only)
- ✅ divergence 결과는 lessons.md에 기록 (참고용)

## 실행 방법
1. 오너가 `evaluator.html` 열기
2. 10개 문제 독립적으로 평가 (E-004·05·06의 답을 모르는 상태로)
3. 결과 텍스트 복사 → 별도 파일로 저장 (예: `owner-calibration-phase8.txt`)
4. evaluator-registry.md에 기록:
   ```
   | Owner | @justant | Calibration | 8 | v2.1-phase8-01 | ... | ✓ Completed | 
   | — | — | — | — | — | — | — | Divergence: +0.XX (diagnostic only) |
   ```

---

# 🚨 주의사항

## 절대 금지
- ❌ **ANSWER-KEY를 평가자에게 보내기** → 블라인드 파괴
- ❌ **평가자 3인과 오너 결과를 섞어서 평균내기** → 규칙 위배
- ❌ **E-001·E-002·E-003(Phase 5 평가자)를 다시 사용하기** (2026-07-05 전)
- ❌ **평가 중도에 ANSWER-KEY를 오너가 들여다보기** → 판단 오염 가능
- ❌ **>60% FAIL 결과를 임의로 출하 진행** → 오너 명시 지시 대기

## 트러블슈팅

### 평가자 회신 늦음 (2~3일 경과)
→ **리마인드 메시지** 전송 (3단계 템플릿 참고)

### 평가자가 "문제가 이상합니다" 보고
→ 오너가 HTML을 다시 열어서 동일 문제를 재현한 후 사용자에게 회신

### 채점 결과가 정확하지 않은 것 같음
→ `scoring-calculator.html` 재입력 또는 수동 계산 재검증

### ANSWER-KEY 재확인 필요
→ `/home/justant/Data/Again-Spring/.result/ai-user-v2/eval/v2.1/phase8/v2.1-phase8-01-answer-key.md` 열기

---

# 📋 최종 실행 체크리스트

```
🟢 준비 단계
☐ Phase 5 평가자 쿨다운 확인 (2026-07-05까지)
☐ 신규 평가자 3명 확보

🟢 1단계: 평가자 모집
☐ E-004·E-005·E-006 연락처 수집
☐ 모집 메시지 전송
☐ 3명 모두 참여 동의

🟢 2단계: 키트 배포
☐ evaluator.html 3명에게 전송
☐ answer-key.md·scoring-calculator.html은 자신에게만 보관
☐ 3명 모두 "파일 받았습니다" 확인

🟢 3단계: 결과 수집
☐ E-004 결과 수신 (형식 확인)
☐ E-005 결과 수신 (형식 확인)
☐ E-006 결과 수신 (형식 확인)
☐ 필요 시 리마인드 전송

🟢 4단계: 채점 & 판정
☐ scoring-calculator.html 또는 수동 계산 실행
☐ 3인 점수 기록
☐ 최종 평균 계산
☐ PASS/FAIL 판정

🟢 5A단계: PASS 시 출하 (PASS 확정 시에만)
☐ dev 배포 현황 확인
☐ e2e-realbe 전체 통과
☐ STATE.md 갱신 + commit + push
☐ prod 상태 확인

🟢 5B단계: FAIL 시 오너 결정 대기 (FAIL 확정 시에만)
☐ steps/v2.1-08d-fail-contingency-design.md 검토
☐ 3가지 옵션 중 선택 (임의 진행 금지)

🟢 6단계: 결과 기록
☐ evaluator-registry.md 갱신
☐ Gate Decision Log 갱신
☐ decisions.md 추가 (PASS 시)
☐ lessons.md 갱신 (PASS 시)
☐ roadmap.md 갱신 (PASS 시)

🟢 보너스: 오너 캘리브레이션 (선택)
☐ 오너 자체 평가 (E-004·05·06 답 모르는 상태)
☐ divergence 기록 (참고용)
```

---

## 📚 참고 문서

| 문서 | 목적 |
|---|---|
| `evaluator-guide.md` | 평가자 모집 및 평가 방법 |
| `v2.1-phase8-01-evaluator.html` | 평가자용 실제 평가 도구 |
| `v2.1-phase8-01-answer-key.md` | 정답 (오너 채점용, SEALED) |
| `scoring-calculator.html` | 자동 채점 도구 |
| `evaluator-registry.md` | 평가자 및 결과 기록 |
| `.result/ai-user-v2/STATE.md` | v2.1 현재 상태 포인터 |
| `.result/ai-user-v2/decisions.md` | 의사결정 로그 |
| `.result/ai-user-v2/steps/v2.1-08b-shipping-checklist.md` | PASS 시 출하 절차 |
| `.result/ai-user-v2/steps/v2.1-08d-fail-contingency-design.md` | FAIL 시 옵션 |

---

**마지막 수정**: 2026-06-22 | **상태**: 즉시 실행 가능 ✅
