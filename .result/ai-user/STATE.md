# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-17 (세션 19 — P0 ✅ corpus 축적 재개, R1 DELETE 승인 대기, R5~R8 진행 중)

---

## ⚠️ 관점 교정 (절대 잊지 말 것)

> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC(0.98~1.0) = "AI가 아직 쉽게 구별됨 = 목표 미달"
> - **`AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 사람이 수동으로 — 코드 변경 금지**

---

## 현재 위치

- **Phase**: Base Hardening 6라운드 (R0~R8) — **P0 ✅** / R5~R8 진행 중 / **R1 DELETE 승인 대기**
- **`AI_USER_ML_ENABLED=false` 유지** / `AI_USER_ML_COLLECT=true` 유지
- **직전 커밋**: `96fdfdcd` (2026-06-17) — R0~R4 + Step 39~43 docs
- **Step 44**: P0 ✅ — 오케스트레이터 재배포 (빌드 00:59:27 > R3 커밋 00:26:53), ML ACCEPTED +3건, e2e 142 passed

---

## 🚨 다음 세션 시작 전 필수 확인

### ① R1 DELETE (사용자 승인 대기) — 세션 19 dry-run 재확인 완료
```bash
cd /home/justant/Data/Again-Spring/.result/ai-user/scripts
python3 audit_mislabels.py --delete
```
- 대상: ctx_* 오염분 34건 (CLIEN 32 + NATEPAN 2) — dry-run 2회 동일 결과
- human_match_delete=0 (과삭제 없음 확인)
- NATEPAN cond4 영향: 2/528 = 0.38% → 미미 → PASS 유지
- **사용자 "삭제 승인" 확인 후에만 실행**

### ② 다음 작업 순서 (R5~R8)
| 단계 | 내용 | 선결 |
|---|---|---|
| **R5** | CLIEN MAUVE 전/후 비교 + 사용자 블라인드 | R4 신선 출력 축적 |
| **R6** | THEQOO corpus 재구축 → 재학습 → cond4 | R3 소스 가드 안착 |
| **R7** | COMMENT MAUVE 전/후 + D-37 길이 | 신선 댓글 축적 |
| **R8** | A-B 동결 + NATEPAN cond4 분기 | R1 결과 |

---

## 6라운드 R0~R8 진행 현황

| 단계 | 내용 | 상태 | 수치 |
|---|---|---|---|
| **P0** | R3 오케스트레이터 재배포 (축적 잠금 해제) | ✅ | 빌드 00:59:27 > R3 00:26:53, ML ACCEPTED +3건, e2e 142P |
| **R0** | clcocloud API-우선 래퍼 (run_ab_test.py) | ✅ | DENY_SIGS 재시도 + CLI 폴백 |
| **R1** | corpus 오라벨 정밀 대조 (audit_mislabels.py) | ⏳ DELETE 승인 대기 | 34건 ctx_* (human_match=0, dry-run 2회 동일) |
| **R2** | 인코딩 방향 회귀 테스트 | ✅ 5/6 passed + 1 xfailed | D-45 확정: 인코딩 정상 |
| **R3** | AS+ML 양면 소스 가드 | ✅ | pushNegative SELF_GENERATED + routes_corpus.py 가드 |
| **R4** | CLIEN de-counselor + features | ✅ | 7 voice.yml + dev DB 5건 JSON_SET |
| **R5** | R4 효과 MAUVE 측정 | 🔄 진행 중 | CLIEN 신선분 축적 중 (현재 n_ai=137) |
| **R6** | THEQOO corpus 재구축 | 🔄 진행 중 | n_ai=0 → ≥100 목표, 자연 틱 축적 중 |
| **R7** | COMMENT MAUVE | 🔄 M-before 진행 중 | 기존 corpus(ai=321, human=1023) 즉시 측정 가능 |
| **R8** | A-B 동결 + cond4 분기 | 🔜 | R1+R5+R6+R7 완료 후 |

---

## 핵심 수치 현황

### AUC (CV 5-fold) — 세션 17 최신값
| 커뮤니티 | AUC (mean) | std | n_human | n_ai | 상태 |
|---|---|---|---|---|---|
| CLIEN | **0.9968** | 0.0053 | 960 | 135 | ✅ 재학습 2026-06-16 |
| DCINSIDE | **1.000** | — | 39 | 105 | INSUFFICIENT_DATA (n_human<300), 장르 불일치 제외 |
| NATEPAN | **0.9989** | 0.00125 | 388 | 226 | ✅ 재학습 2026-06-16, P(human) 방향 정상 |
| THEQOO | **학습 불가** | — | 376 | **0** | 🚨 ai=0건 (541건 삭제). 재수집 필요 |

### MAUVE (POST) — 세션 19 재측정
| 커뮤니티 | MAUVE | n_human | n_ai | 비고 |
|---|---|---|---|---|
| CLIEN | **0.6277** | 960 | 142 | R4 신선분 포함 전체 corpus (Step 45) |
| DCINSIDE | **0.9999** | — | — | 이전 값 유지 |
| NATEPAN | **0.8395** | 388 | 226 | 세션 19 재측정 |
| THEQOO | — | 376 | 0 | R6 재수집 중 (ai=7건 시작) |

### MAUVE (COMMENT) — 세션 19 R7 M-before
| 커뮤니티 | MAUVE | n_human | n_ai | 비고 |
|---|---|---|---|---|
| CLIEN | **0.0677** | 1023 | 321 | Step 47 M-before |
| NATEPAN | **0.0598** | 1114 | 303 | Step 47 M-before |

### A-B 테스트 (최신)
| 커뮤니티 | Δ | std | cond4 |
|---|---|---|---|
| NATEPAN | **+0.1667** | 0.1257 | ✅ PASS |
| CLIEN | 0.0000 | — | ❌ MAUVE ceiling (0.9962) |
| THEQOO | — | — | ⛔ corpus ai=0 측정불가 |

### ENABLE 게이트 (5조건, D-17)
```
cond1: ✅ THEQOO/CLIEN/NATEPAN n_ai≥100
cond2: ✅ 3개 커뮤니티 AUC 신뢰 가능
cond3: ✅ SPLITTER_VERIFIED=True
cond4: ✅ NATEPAN Δ=+0.1667 PASS
       ⛔ THEQOO 측정불가 (corpus ai=0)
       ❌ CLIEN Δ=0 (MAUVE ceiling)
cond5: ❌ FAIL — 사용자 정확도 82.5% (목표 ≤60%)
```

---

## R2 xfail 현황 (기록용)

`test_formal_texts_have_low_human_prob` — NATEPAN 현재 상태:
- 격식체 text0: P(human)=0.6791
- 격식체 text2: P(human)=0.9967 ← xfail 원인
- `test_slang_higher_than_formal`: PASS (슬랭 > 격식 방향 정상)
- **R1 DELETE + 재학습 후 strict 0.5 기준으로 복원 예정**

---

## 운영 메모

- **Auto 모드**: 막히지 않으면 계속 진행 (사용자 명시, 2026-06-02)
- **WSL CPU**: 20코어, 최대 18개 에이전트 병렬 (2026-06-17 갱신)
- **로컬**: 최대 8개 에이전트 병렬
- **VRAM 권한**: RTX 3090(25.8GB) WSL, 1주 단위 갱신 필요
- **R1 DELETE**: 사용자 "삭제 승인" 필요 (절대 규칙)
- **prod 배포**: 명시 지시 + 절대규칙 #4

---

## 특이사항 / 함정 (세션 간 공유 필수)

### [S18] CLIEN personas 세대 불일치
- DB 활성 CLIEN 5개 = PersonaFactory 자동 생성 (persona target=100 포화)
- voice.yml 7개 개별 프로필(036, 081~086)은 DB에 미반영
- → voice.yml 변경은 **DB에 직접 JSON_SET 필요** (R4에서 5건 적용 완료)
- 새 persona 추가하려면 target 상향 또는 DB 재시드 필요

### [S18] R2 xfail 맥락
- NATEPAN corpus에 AI 상담조 텍스트가 human으로 오라벨 (ctx_* 제외 후에도 일부)
- R1 DELETE 후 재학습하면 격식체 P(human) 0.5 미만으로 개선 기대
- 방향 자체(슬랭>격식)는 정상 — D-45 확정

### [S17] THEQOO corpus ai=0건
- 541건 전량 삭제 (2026-06-16, 사용자 승인)
- 재수집: 오케스트레이터 자연 틱 (THEQOO 봇 POST) → n_ai≥100 목표
- R3 소스 가드 안착 후 신뢰할 수 있는 재수집 시작

### [S17] voiceBlockForPost features 경로
- ActionExecutor.appendWritingQuirks()가 writing_quirks.features를 읽어 [문체 패턴] 섹션 주입 (T8 수정 완료)
- dev DB의 CLIEN persona들은 R4 JSON_SET으로 features 반영됨

### [이전] Python 테스트 모듈 캐싱
- `patch("app.storage.db.get_session")` 실패 → 사용 지점 패치 필요
- routes_eval.py → `patch("app.api.routes_eval.get_session")`

---

## 전체 Step 인덱스

| Step | 세션 | 내용 | 상태 |
|---|---|---|---|
| Step 0~17 | 1~10 | 스캐폴드~T8 THEQOO TSD | ✅ |
| Step 18~26 | 11~13 | 2라운드 N1~N9 | ✅ |
| Step 27~34 | 14~16 | 3라운드 M1~M8 + CUDA 수정 | ✅ |
| Step 35~38 | 16~17 | M1 A-B 재실행, M6 댓글, NATEPAN 교정, THEQOO corpus 삭제 | ✅ |
| Step 39~43 | 18 | 6라운드 R0~R4 (API래퍼·소스가드·CLIEN de-counselor) | ✅ |
| **Step 44** | 19 | P0: R3 오케스트레이터 재배포 + e2e + corpus 축적 확인 | ✅ |
| **Step 45** | 19 | R5: CLIEN POST MAUVE (M-before=0.6277, 신선분 축적 중) | 🔄 |
| **Step 46** | 19 | R6: THEQOO corpus 재구축 (ai=0→7건, 목표 ≥100) | 🔄 |
| **Step 47** | 19 | R7: COMMENT MAUVE M-before (CLIEN 0.0677, NATEPAN 0.0598) | 🔄 M-after 대기 |
| Step 48 | 19~ | R8: cond4 분기 + 5조건 최종 | 🔜 |
