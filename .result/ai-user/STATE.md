# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-16 (세션 16 — M1 cond4 재측정 완료(FAIL), M8 DCINSIDE 장르불일치 제외)

---

## ⚠️ 관점 교정 (절대 잊지 말 것)

> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC(0.98~1.0) = "AI가 아직 쉽게 구별됨 = 목표 미달"
> - "AUC≥0.55=ready"는 **"리랭커 배포 가능"** 만 의미 — 절대 "사람 같다" 아님
> - **`AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 사람이 수동으로 — 코드 변경 금지**

---

## 현재 위치

- **Phase**: Base Hardening 3라운드 (M1~M8) 진행 중
- **다음 단계**: Base Hardening 3라운드 (M1~M8) 진행 중
- **`AI_USER_ML_ENABLED=false` 유지** / `AI_USER_ML_COLLECT=true` 유지
- **완료**: N1 ✅ · N2 ✅ · N3 ✅ · N4 ✅ · N5 ✅ (cond5 FAIL) · N6 ✅ · N7 ✅ · N8(a) ✅ · N8(b) ✅ · N8(c) ✅ · N9 ✅ | 3라운드 M1~M8 진행 중

---

## 전체 완료 현황 (Step 0~17)

| Step | 내용 | 상태 | 핵심 수치 |
|---|---|---|---|
| Step 0 | 스캐폴드 + 문서 시스템 | ✅ | WSL ML 서비스 8201 기동 |
| Step 1 | KatFishNet 피처 추출기 | ✅ | 24/24 pytest |
| Step 2 | 코퍼스 파이프라인 | ✅ | human 4개 커뮤니티 적재 |
| Step 3 | 평가 하네스 + 베이스라인 | ✅ | DCINSIDE/NATEPAN/THEQOO/CLIEN |
| Step 4 | 판별기 학습 + 스코어 엔드포인트 | ✅ | GPU 40초, AUC 확보 |
| Step 5 | AS Best-of-N 와이어링 | ✅ | AiUserMlClient + /rerank |
| Step 6 | 분포매칭 개편 | ✅ | OutputSanitizer + SelfCritique |
| Step 7 | 주기 갱신 + 모니터링 | ✅ | 6h/24h 루프 |
| Step 8 | COLLECT/ENABLED 플래그 분리 | ✅ | AI_USER_ML_COLLECT=true prod 배포 |
| Step 9 | AI negative 백필 | ✅ | 5803행, 첫 실제 AUC |
| Step 10 (T1) | 문장 분리기 수정 | ✅ | DC avg_sl 57.40→7.02 |
| Step 11 (T2) | CV AUC + 위조 금지 | ✅ | INSUFFICIENT_DATA 게이트 |
| Step 12 (T3) | readiness 버그 수정 | ✅ | NATEPAN ready=false |
| Step 13 (T4) | COMMENT 측정 추가 | ✅ | COMMENT MAUVE 0.06 |
| Step 14 (T7) | ENABLE 게이트 구현 | ✅ | 0/12 (정상) |
| Step 15 (T6) | 독립 검증 A-B harness | ✅ | THEQOO Δ=-0.356, CLIEN Δ=0 |
| Step 16 (T5) | POST 샘플 보강 | 🔜 | n_ai 미달 (자연 축적 중) |
| Step 17 (T8) | THEQOO TSD 프롬프팅 | ✅ | [문체 패턴] 주입 완료 |

---

## 핵심 수치 현황

### AUC (CV 5-fold) — M3 재학습 후 최신값
| 커뮤니티 | 최신 AUC (mean) | AUC std | n_human | n_ai | 상태 |
|---|---|---|---|---|---|
| CLIEN | **0.9947** | 0.0095 | 960 | 131 | ✅ cond1/cond2. 재학습 2026-06-16 09:10 (idempotency). Model 01KV7V3EJXV3RB1S4SANY05NBX |
| DCINSIDE | **1.000** | — | 39 | 105 | INSUFFICIENT_DATA (n_human<300) — M8에서 재-pull 필요 |
| NATEPAN | **0.9994** | 0.00086 | 388 | 225 | ✅ cond1/cond2. 재학습 2026-06-16 09:10. Model 01KV7V33HB9BQJEZG5E7DWRJ99 |
| THEQOO | **0.9986** | 0.00275 | 376 | 158 | ✅ cond1/cond2 (ctx_* 11행 삭제 확인: 544→534 n_train). Model 01KV7V3A1AR9YA39P5JVH5KX0H |

> AUC가 높다 = AI가 쉽게 구별됨 = **목표 미달 상태**. n_ai≥100 후 재학습 필요.

### MAUVE (POST, 오케스트레이터 실제 봇 코퍼스 기준)
| 커뮤니티 | MAUVE | 비고 |
|---|---|---|
| CLIEN | **0.970** | 우수 (ceiling 근접) |
| DCINSIDE | **0.9999** | 최우수 |
| NATEPAN | **0.8395** | ✅ 2026-06-16 10:31:58 baseline 완료 (eval_run id=78) |
| THEQOO | **0.6077** | T8 효과 확인 ✅ (before: 0.345 → after: 0.6077, Job 01KV7HZYECXC5VZRGW5Q88RTWW) |

### A-B 테스트 결과 (M1 재측정 완료, 2026-06-16 K=3시드)
| 커뮤니티 | MAUVE(rerank) | MAUVE(random 3seed 평균) | std | Δ | n_ctx | cond4 |
|---|---|---|---|---|---|---|
| THEQOO | 0.9815 | 0.9908 | 0.0098 | **−0.0094** | 16 | ❌ FAIL (Δ<0) |
| NATEPAN | 0.8273 | 0.8441 | 0.0801 | **−0.0167** | 40 | ❌ FAIL (Δ<0, 노이즈) |
| CLIEN | 0.9962 | 0.9962 | — | **0.0000** | 12 | ❌ MAUVE ceiling |

> **이전 THEQOO Δ=+0.4834**: 12ctx 단일런 노이즈 — K=3 재측정에서 Δ=−0.0094로 확정.

### ENABLE 게이트 (5조건)
```
현재: 3+/12 커뮤니티×조건 충족 (N9 Round3 이후)
상태:
  cond1: ✅ THEQOO n_ai=158≥100, CLIEN n_ai=131≥100, NATEPAN n_ai=225≥100
         ❌ DCINSIDE: 장르 불일치 → 제외 (n_human=39, 콘텐츠=와인/카메라/여행, 갈등 서사 아님)
  cond2: ✅ THEQOO/CLIEN/NATEPAN AUC 신뢰 가능 (n_human≥300, n_ai≥100 모두 충족)
         ❌ DCINSIDE AUC 신뢰 불가 (n_human=39 << 300)
  cond3: ✅ SPLITTER_VERIFIED=True (N3 수정)
  cond4: ❌ THEQOO Δ=−0.0094 (K=3, 16ctx) — 판별기 역전으로 리랭킹 역효과
         ❌ NATEPAN Δ=−0.0167 (K=3, 40ctx, std=0.0801 노이즈)
         ❌ CLIEN Δ=0 (MAUVE ceiling 0.9962)
  cond5: human_accuracy=1.0 (THEQOO/CLIEN) — 프롬프트 개선 후 재라벨링 필요
```

---

## Base Hardening 2라운드 진행 현황 (Step 18~26)

| Step | 작업 | 상태 | 핵심 수치 |
|---|---|---|---|
| N4 (Step 21) | 15-ab-harness.md VOID 헤더 | ✅ | commit aa39e042 |
| N2 (Step 19) | D-21 분리기 단위테스트 | ✅ WSL | 13/13 PASS, DC avg_sl=2.62 (commit 73f227c) |
| N3 (Step 20) | enable-gate cond3/cond5 정정 | ✅ WSL | cond3=True, cond5 ≤0.60 (commit dac259b) |
| N1 (Step 18) | THEQOO 코퍼스 디오염 | ✅ WSL | 168/344 삭제, 252 클린. P(human) 역전 유지(재학습 필요) |
| N5 (Step 22) | 블라인드 자가 라벨링 | ✅ | 정확도 1.00(THEQOO/CLIEN) — cond5 FAIL(예상). eval_run id=50/51 기록 |
| N6 (Step 23) | 댓글 초성체 allowChosung | ✅ | commit 68cb4781 — e2e dev:8090 통과 확인 |
| N7 (Step 24) | DB general_style 정정 | ✅ | dev DB 100개 페르소나 큐레이션 스타일 + PersonaFactory voiceGuide |
| N8(a) (Step 25) | NATEPAN/INVEN HEAVY 승격 | ✅ | NATEPAN HEAVY=2, INVEN HEAVY=2. voice 필터 트리거 추가 |
| N8(b) (Step 25) | AI POST 생성 n_ai→100 | ✅ 완료 | THEQOO n_ai=157, CLIEN n_ai=131, NATEPAN n_ai=225 |
| N8(c) (Step 25) | 전체 재학습 + AUC 검증 | ✅ 완료 | NATEPAN AUC=0.9988, 전체 커뮤니티 재학습 완료 |
| N9 (Step 26) | 클린 A-B + T8 MAUVE | ✅ 완료 | A-B Round3: THEQOO Δ=+0.4834(⚠️UNVERIFIED 단일런노이즈), CLIEN Δ=0, NATEPAN Δ=0 |

---

## ⚠️ 특이사항 / 주요 발견 (세션 간 공유 필수)

### 1. voiceBlockForPost 무출력 버그 (T8에서 발견)
`ActionExecutor.appendWritingQuirks()`가 `writing_quirks.features` 필드를 코드에서 **전혀 읽지 않았음** (dead field).
THEQOO 시스템 프롬프트의 `## 페르소나 특성` 섹션이 persona_style 텍스트 한 줄뿐이었음.
→ **수정 완료**: `[문체 패턴]` 섹션 추가, dev DB 7개 페르소나 JSON_SET.

### 2. DB 페르소나 세대 불일치
- `ai-user/docs/personas/profiles/ai-user-{N}/voice.yml` persona_ids **≠** DB 페르소나 IDs
- DB 100개 페르소나는 다른 세대에서 auto-generated → voice.yml 변경이 자동 반영 안 됨
- **운영 주의**: voice.yml 변경은 DB에 **직접 JSON_SET** 필요 (또는 전체 재시드)
- 프로덕션 T8 배포 시 prod DB에도 동일 SQL 실행 필요

### 3. THEQOO 판별기 역전 (코퍼스 오염)
- THEQOO 인간 코퍼스에 링크포스트·공지·짧은 반응 대거 혼입
- 판별기: "긴 갈등 서사=AI, 짧은 반응/링크=인간" 학습 → P(human) 역방향
- `/rerank`: 격식체 "당신의..." → P(human)=0.92, 더쿠 슬랭 "어제 남친이 또ㅠㅠ" → P(human)=0.10
- **근본 수정**: THEQOO 인간 코퍼스를 갈등 사연 POST만으로 필터링 필요

### 4. A-B 긍정 발견
- `run_ab_test.py` 단순 THEQOO 프롬프트 → MAUVE=0.985 (랜덤 초안 기준)
- 오케스트레이터 실제 봇 출력 MAUVE=0.345
- **함의**: 모델 capability가 아닌 오케스트레이터 프롬프트 구성의 문제 → T8으로 해결 시도

### 5. CLIEN 이미 우수
- MAUVE=0.9998, A-B Δ=0 (무신호) → Best-of-N이 CLIEN에서 효과 없음
- CLIEN은 리랭커 없이도 이미 인간 분포와 동일 수준

### 6. 멀티코어 최적화 (WSL 20코어)
- pytest: `pytest -n auto` (pytest-xdist 설치됨)
- sklearn: `n_jobs=-1`
- Python 병렬: `ThreadPoolExecutor(max_workers=8)`
- run_ab_test.py: `--workers 8` (10컨텍스트×4초안=40호출 ~30초)

### 7. Python 테스트 모듈 캐싱 함정
- `from app.storage.db import get_session` 임포트 시점에 바인딩 캐시
- `patch("app.storage.db.get_session")` 실패 → 사용 지점에서 패치 필요
- `routes_eval.py` → `patch("app.api.routes_eval.get_session")`
- `routes_corpus.py` → `patch("app.api.routes_corpus.get_session")`

### 8. Corpus Ingest 보강 (세션 12) + M1 강등 (세션 14)
- AS example_bank에서 추가 인간 POST 적재:
  - CLIEN: 294→974 (+680개)
  - THEQOO: 256→387 (+131개)
  - NATEPAN: 확장 (n_human=445)
- 전체 재학습: CLIEN AUC=0.9955, THEQOO AUC=0.9994, NATEPAN AUC=0.9988
- **Round 3 A-B (Post-Ingest 新모델)**: THEQOO Δ=+0.4834 ← ⚠️ **UNVERIFIED 단일런 노이즈** (M1에서 강등)
  - 원인: 무시드 random.randint() + 12ctx + 3런 random arm = 0.9111/None/0.4961
  - CLIEN Round 3: MAUVE=0.9962 (ceiling) → Δ=0

### 9b. M2 발견 (세션 14) — P(human) 방향 N1 이후에도 여전히 역전
- THEQOO discriminator 스팟체크: 슬랭서사→P(human)=0.0000044, 격식AI→P(human)=0.9976 ❌ 완전 역전
- 코드 버그 아님 — `predict_proba[:, 1]` = P(class=1=human) 정상
- **실제 원인**: T8 이후 AI corpus(label=ai)에 슬랭체 출력 축적 → 판별기가 "슬랭=AI, 격식=human" 역학습
- **함의**: Best-of-N 현재 활성화 시 최악 출력을 winner 선택 (AI_USER_ML_ENABLED=false 유지 필수)
- **M3 추가 과제**: corpus 장르 필터 — 갈등 서사 POST만 남기기 (링크 제거만으론 부족)

### 9. DCINSIDE n_ai milestone (세션 12)
- DCINSIDE n_ai: 88 → 103 (2026-06-16 trigger로 100 돌파!)
- 단, n_human=39 << 300 — 학습 불가능 (cond2 FAIL, AUC 신뢰도 낮음)
- 우선순위: DCINSIDE human corpus 확보 (현재 39개, 261개 추가 필요)

### 10. M5 블라인드 테스트 준비 완료 (세션 16)
- `.result/ai-user/m5-blind-display.txt`: 40쌍 (NATEPAN 20 + THEQOO 20)
- NATEPAN human 샘플: 갈등 키워드 필터 적용 (남편/시어머니 등) — 10건
- THEQOO human 샘플: 갈등 필터 미통과 (4건뿐), 랜덤 10건 사용 — 해석 주의
- 사용자가 m5-blind-display.txt 보고 H/A 라벨링 → 정확도 산출 후 cond5 기록

---

## 다음 세션 작업 목록 (3라운드 M1~M8 진행 중)

### 완료 (세션 14~15)
- ✅ cond4 UNVERIFIED 강등 (STATE.md/roadmap/steps/decisions 갱신)
- ✅ M2 P(human) 스팟체크 — 역전 확인, 코드 버그 아님, corpus 장르 편향이 원인 (steps/28-m2-p-human-spotcheck.md)
- ✅ M3 — ctx_* 22행 삭제(THEQOO 11, CLIEN 9, NATEPAN 2) + NATEPAN/CLIEN 디오염
- ✅ M3 재학습 — THEQOO/CLIEN/NATEPAN `/train` 완료 (새 CV-AUC 추출)
- ✅ M4 CV 추출 — THEQOO mean=0.9986/std=0.00275, CLIEN 0.9947/0.0095, NATEPAN 0.9994/0.00086 (steps/30-m4-cv-ablation.md)
- ✅ M7 (파일럿 완료) — NATEPAN voice.yml 16개 features 백필 + GenDto.ReplyRequest voiceType 필드 추가 + ActionExecutor reply voiceType 전달 + GenerationController/SelfCritiqueService voiceType 경로 보정 + PersonaFactory schema features 추가 + dev DB NATEPAN 6개 JSON_SET + dev 배포 + e2e 142/142 통과 (5 skip)

### ✅ 완료 (세션 16)
- ✅ M1 A-B 재측정 — K=3 시드, routes_eval.py 배포, THEQOO Δ=−0.0094 FAIL, NATEPAN Δ=−0.0167 FAIL
- ✅ M8 DCINSIDE — 장르 불일치 확인 (와인/카메라/여행), 학습 제외 결정
- ✅ DB 스키마 수정 — jobs.params_json MEDIUMTEXT (40ctx 500 에러 해소)

### 🥇 다음 우선순위
1. **M5 블라인드** — ✅ 준비 완료. `.result/ai-user/m5-blind-display.txt` 생성 (NATEPAN 20쌍, THEQOO 20쌍). **사용자 라벨링 대기 중.**
2. **M6 COMMENT MAUVE** — before/after N6 측정 (아직 NOT RUN)
3. **cond4 경로** — M7 신선 출력 축적 → 재학습 → P(human) 방향 교정 → A-B 재측정

### ✅ M5 블라인드 (세션 16 준비 완료)
- 사용자 직접 라벨링 40쌍 (THEQOO+NATEPAN 각 20쌍)
- M7 features(NATEPAN) + voiceType reply 경로 적용 → 신선 출력 dev에서 자연 틱 중
- `.result/ai-user/m5-blind-display.txt`: 40쌍 준비 완료

### 🥉 M6 COMMENT MAUVE
- before/after N6 측정 (아직 NOT RUN)

---

## 운영 메모

- **Auto 모드**: 막히지 않으면 계속 진행 (사용자 2026-06-15 명시)
- **VRAM 권한**: 2026-06-15~약 2026-06-22 (1주) WaggleBot VRAM 전부 unload 가능
- **WSL CPU**: 20코어, 최대 16 사용 가능 — 항상 멀티코어 옵션 사용
- **블로커**: 없음

## 미해결 질문

- THEQOO cond4 ✅ 달성. CLIEN/NATEPAN은 Δ=0 — 코퍼스 보강+재학습으로도 Δ>0 안 나오면 아키텍처 변경 필요한지?
- CLIEN MAUVE 천장(0.9962): cond4를 Δ≥0로 재정의할지, human_accuracy 기반으로 전환할지?
- 프롬프트 개선으로 cond5 human_accuracy≤0.60 달성 가능한가?
- DCINSIDE n_human=39 → 300: example_bank(336개) 에서 추가 확보 가능한가? (261개 필요)
