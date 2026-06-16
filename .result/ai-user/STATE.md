# STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-16 (세션 11 — N1~N7+N8a 완료, N8b 진행 중)

---

## ⚠️ 관점 교정 (절대 잊지 말 것)

> - **프로젝트 성공** = AUC→0.5, MAUVE→1.0, 사람 블라인드 정확도→~50%
> - 높은 AUC(0.98~1.0) = "AI가 아직 쉽게 구별됨 = 목표 미달"
> - "AUC≥0.55=ready"는 **"리랭커 배포 가능"** 만 의미 — 절대 "사람 같다" 아님
> - **`AI_USER_ML_ENABLED=true` 활성화는 5조건(D-17) 전부 충족 후 사람이 수동으로 — 코드 변경 금지**

---

## 현재 위치

- **Phase**: Base Hardening 2라운드 진행 중 (Step 18~26)
- **`AI_USER_ML_ENABLED=false` 유지** / `AI_USER_ML_COLLECT=true` 유지
- **완료**: N1 ✅ · N2 ✅ · N3 ✅ · N4 ✅ · N5 ✅ (cond5 FAIL) · N6 ✅ · N7 ✅ · N8(a) ✅ · **진행**: N8(b) 🔄

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

### AUC (CV 5-fold)
| 커뮤니티 | 최신 AUC | n_ai(POST) | 상태 |
|---|---|---|---|
| CLIEN | **0.989** | ~40 | INSUFFICIENT_DATA (n_ai<100) |
| DCINSIDE | **1.000** | ~20 | INSUFFICIENT_DATA (n_ai<100) |
| NATEPAN | **0.562** | 0 | INSUFFICIENT_DATA (POST 0) |
| THEQOO | **0.980** | ~65 | INSUFFICIENT_DATA (n_ai<100) |

> AUC가 높다 = AI가 쉽게 구별됨 = **목표 미달 상태**. n_ai≥100 후 재학습 필요.

### MAUVE (POST, 오케스트레이터 실제 봇 코퍼스 기준)
| 커뮤니티 | MAUVE | 비고 |
|---|---|---|
| CLIEN | **0.970** | 우수 (ceiling 근접) |
| DCINSIDE | **0.9999** | 최우수 |
| NATEPAN | null | AI POST 0개 (N8a 완료 후 생성 예정) |
| THEQOO | **0.345** | 최하 — T8 적용 후 재측정 필요 (N9) |

### A-B 테스트 결과 (Step 15, 2026-06-16)
| 커뮤니티 | MAUVE(rerank) | MAUVE(random) | Δ | cond4 |
|---|---|---|---|---|
| THEQOO | 0.629 | 0.985 | **-0.356** | ❌ 역전 |
| CLIEN | 0.9998 | 0.9998 | **0.000** | ❌ 무신호 |

### ENABLE 게이트 (5조건)
```
현재: 0/12 커뮤니티×조건 충족 (정상 — N8b 진행 중)
blocker:
  cond1: n_ai<100 (THEQOO 68, CLIEN 41, DCINSIDE 24, NATEPAN 0 — N8b 중)
  cond2: CV-AUC 신뢰 불가 (n_ai<100 → INSUFFICIENT_DATA)
  cond3: ✅ SPLITTER_VERIFIED=True (N3 수정)
  cond4: Δ<0 (THEQOO 역전) / Δ=0 (CLIEN 무신호) — 재학습 후 N9 재측정
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
| N8(b) (Step 25) | AI POST 생성 n_ai→100 | 🔄 진행 중 | voice 필터 트리거 실행 중 — 2026-06-16 결과 대기 |
| N9 (Step 26) | 클린 A-B + T8 MAUVE | ⏳ 대기 | N8(b) n_ai≥100 + `/train` 완료 필요 |

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

---

## 다음 세션 작업 목록 (우선순위 순)

### 🥇 우선순위 1 — T8 효과 검증 (비용 0, WSL)
```bash
# WSL에서 실행
curl -X POST http://100.115.252.61:8201/eval/baseline \
  -H "Authorization: Bearer aiuser-ml-api-token-dev-2026"
# → THEQOO MAUVE before(0.345) / after(T8 적용 후) 비교
```
- 신선 THEQOO 봇 게시글이 쌓인 후 의미 있는 비교 가능
- T8 커밋 이후 오케스트레이터가 자동으로 새 글 생성 중 (틱마다 THEQOO 페르소나 활동)

### 🥈 우선순위 2 — T5 (Step 16): n_ai → 100
- THEQOO: 현재 65 POST, 35개 더 필요 (가장 근접)
- DCINSIDE: 현재 ~20, 80개 더 필요
- NATEPAN: 0 POST — 오케스트레이터가 NATEPAN 게시글 생성 설정 검토 필요
  - `ActionPlanner`가 NATEPAN 페르소나에게 POST 행동을 배정하는지 확인
- CLIEN: 현재 ~40, 60개 더 필요

### 🥉 우선순위 3 — THEQOO 코퍼스 정제 (T6 재실행 전제)
- WSL에서 `corpus_item` 테이블에서 THEQOO human 항목 점검
  ```sql
  SELECT text FROM corpus_item WHERE community='THEQOO' AND label='human' AND LENGTH(text)<50;
  ```
- 짧은 반응/링크/공지 제거 (길이 필터: <50자 or URL 포함)
- 정제 후 `/train` → `/eval/ab-test` 재실행 → cond4 재검증

### 📋 잔여 작업 (이후)
- cond5 (사람 블라인드): `/corpus/export/blind` 내보낸 JSONL을 사람이 라벨링 → `/eval/human-blind` 기록
- prod 배포: DB SQL 업데이트 포함 (T8 적용 명시 지시 시)
- 모든 커뮤니티 n_ai≥100 + AUC 재학습 + A-B 재검증 후 → D-17 5조건 체크

---

## 운영 메모

- **Auto 모드**: 막히지 않으면 계속 진행 (사용자 2026-06-15 명시)
- **VRAM 권한**: 2026-06-15~약 2026-06-22 (1주) WaggleBot VRAM 전부 unload 가능
- **WSL CPU**: 20코어, 최대 16 사용 가능 — 항상 멀티코어 옵션 사용
- **블로커**: 없음

## 미해결 질문

- NATEPAN 봇 POST가 왜 0인가? ActionPlanner가 NATEPAN에 POST를 배정하지 않는 건지 확인 필요
- THEQOO 코퍼스 정제 후 재학습 시 n_ai가 여전히 <100이면 INSUFFICIENT_DATA → T5 선행 필수
- CLIEN Best-of-N은 의미 없음 확인 → CLIEN은 cond4 이외 기준으로 enable 여부 결정 필요한지?
