# AI-User v3 — Charter (스타일 수렴 & 생성 품질 도약)

> **라운드**: v3 "Style Convergence & Generation Quality Leap"  
> **시작일**: 2026-06-22 (준비 중)  
> **전임**: AI-User v2.1 (SHIPPED 2026-06-22, 5인 28% 식별률 PASS)  
> **창립 진단**: v2.1 post-ship 레버 튜닝(T5·T6·T7) + 잔존 과제(T8·thin plaza·comment 품질)

---

## 프로젝트 명제

### v2.1 완성 상태 (2026-06-22)

**v2.1 Phase 8 최종 측정** (10계정, 블라인드, 캐주얼 독자 5인):
- **AI 식별률**: 5/18 = 27.8% (28% PASS)
- **Human 오탐률**: 55% (역전 — 구분 불가 수준)
- **결론**: 생성 스타일이 카테고리 정렬 한계에 도달. 추가 진전은 구조적 개선 필요.

**Post-Ship 튜닝** (2026-06-22, commit 8c84b58f):
- **T6 과교정문법**: THEQOO/BLIND typoProb 상향 (0.18→0.385)
- **T7 슬랭부재**: NATEPAN/GENERAL chosungInject 추가
- **T5 어휘이질**: 문어체 denylist 13종 추가
- **측정**: AI 슬랭 44.3% vs 인간 19.9% (분포 기기 필요)

**잔존 약점**:
1. **T8 비응집** — 감정이 사건을 따라가지 못함. 문장 간 결속력 부족
   - 예: "팀장이 X했다. 팀장이 Y했다. 팀장이 Z했다." → 감정 없는 사건 목록
   - 범위: 구조적·생성 모델 레벨 (필터/prompt 범위 밖)
2. **Thin plaza 부족** — FRIEND(165건), WORK(156건) corpus 현저히 부족
   - v2 성공한 광장: COUPLE/MARRIED/OTHER (충분함)
   - 약점 광장: FRIEND/WORK (코퍼스 3-4배 확대 필요)
3. **Comment 품질 미측정** — POST만 평가해옴
   - 댓글은 POST보다 단문 → 식별 tell 적음 예상
   - 혼합 키트 필요 (POST 3 + COMMENT 3 per 계정)

### v3 차별점 (v2.1에서 계승)

**v2.1 성과**:
- 카테고리 정렬이 tell의 30%를 자동 해결 (맥락 불일치 소멸)
- 캐주얼 독자 타깃 확보 = 게이트 평가 신뢰성 100배 향상
- 규율 R1~R8 정착 = 과학적 재현성 확보

**v3 추가 3-트랙**:
1. **T8 비응집** — QLoRA fine-tuning으로 감정·사건 응집성 향상
   - 데이터: 갈등 커뮤니티 고품질 POST ≥500건 SFT format
   - 모델: Haiku 4.5 → QLoRA (WSL RTX 3090)
   - 어댑터: `ai-user/learning/adapters/haiku-v3-01/`
2. **Thin plaza 보강** — FRIEND/WORK 코퍼스 300→400+ (각 3-4배)
   - 소스: DCINSIDE 결혼/연애 갤러리 + CLIEN 직장인 게시판 + 인스티즈 우정/인간관계
   - 제외: Blind.co (robots.txt 명시 차단)
   - 정화: 기존 v2.1 기준 (한글<10%, URL-heavy, content_hash dedup)
3. **Comment 품질 첫 측정** — 혼합 평가 키트 설계
   - 키트: 계정당 POST 3 + COMMENT 3 (단순히 POST만 늘리는 게 아님)
   - 지시문: "이 계정의 글·댓글을 모두 봤을 때 사람인가 봇인가"
   - 예측: POST 단독보다 식별률 낮을 가능성 높음

---

## 성공 기준

> **"강화된 광장별 계정(thin plaza+QLoRA+comment 포함)을, 신선 캐주얼 독자 ≥4인이 블라인드로 봤을 때 봇으로 안 보는 수준"**

- **타깃 독자**: 신선 캐주얼 일반 독자 (포렌식 ×), **≥4인 합의** (v2.1 ≥3인 강화)
- **평가 단위**: 광장별 계정 타임라인 + 댓글 섞임 (v2.1은 POST만)
- **공동체**: NATEPAN 전용 (v2.1 승계)
- **오라클**: 사람 블라인드 단일
- **강화점**: thin plaza(FRIEND/WORK) **필수 포함** (v2.1 약점 보강)

---

## Kill Criterion (v3)

```
✅ 등록 예정: 2026-06-22 Phase 0

신선 캐주얼 독자 ≥4인, 광장별 계정 타임라인 블라인드에서
(FRIEND·WORK 광장 필수 포함, POST 3 + COMMENT 3 per 계정)

평균 봇 식별률 ≤ 25% = PASS

→ PASS : QLoRA 어댑터 + thin plaza + comment 가이드 prod 배포
→ FAIL : QLoRA 재학습 옵션 / 추가 필터 옵션 제시 (오너 결정)
```

**오너 확정 대기**: kill criterion ≤25% 수치(제안), 평가자 수(제안 ≥4인), 광장별 순서(제안 random), HARDER KIT 구성(제안 AI 5 + Human 5, FRIEND/WORK 필수).

---

## v3 표준 규율 (R1~R8 승계 + v3 구체)

| # | 규율 | v3 구체 |
|---|---|---|
| R1 | **단위 = 광장별 계정 타임라인** | 6 광장 + thin plaza 보강 (FRIEND/WORK) |
| R2 | **proxy 사다리 금지** | 분포 sanity만 허용, humanness 판정 금지 |
| R3 | **변수 고정** | NATEPAN 전용. Phase별 변수 1개. Phase 2 comment는 예외(신규 데이터) |
| R4 | **저빈도 고정보 eval** | baseline(Ph5) 1회 + 최종(Ph8) 1회. named-tell 라벨셋 갱신(T8 추가) |
| R5 | **kill criterion 사전 등록** | 위 등록 예정(오너 확정 대기) |
| R6 | **판별기 = QA만** | rerank OFF (D-108 유지). QLoRA는 생성 모델 레벨(판별기 아님) |
| R7 | **v2.1 제약 승계** | `AI_USER_ML_ENABLED=false` 영구. 어댑터는 e2e test로만 검증 |
| R8 | **main 단일·docs-as-code·prod 게이트** | 절대규칙 #4·#8·#9 |
| R9 | **thin plaza 수동 시드** | robots.txt 차단(Blind.co) 존중. Phase 1 정화 기준 유지 |
| R10 | **comment eval = 혼합 키트** | POST+COMMENT 섞은 지시문(새로움) |

**Anti-pattern (금지)**:
- 오너 게이트 평가 / whack-a-mole / thin plaza를 회피로 정당화
- 새 크롤러 신설(기존 5개 + DCINSIDE/CLIEN/인스티즈만)
- QLoRA 데이터게이트 조건 미충족 발동
- comment 측정 스킵(prod 출하 전 필수)
- proxy/MAUVE/LLM-judge 부활

---

## 핵심 코드 훅 (v2.1 승계 + v3 추가)

| 레버 | 위치 | v3 역할 |
|---|---|---|
| 광장 enum | `backend/.../PostCategory.java:12-18` | 분류·생성·RAG taxonomy 고정 |
| 카테고리 고정 | `ActionExecutor.topCategory`(:1506-1513), `profile.yml interests` | Phase 1 thin plaza 페르소나 추가 |
| RAG 검색 | `examples.py:87-185`(3단 폴백) · `AiLearningClient.findSimilar`(:154-167) | Phase 1 코퍼스 갱신 후 RAG 재지수화 |
| 분류기 | `EmbeddingService.embed_batch`(`embedding.py:31`) | Phase 1 FRIEND/WORK 임베딩 추가 |
| QLoRA 어댑터 | `LlmWorkerPool` 통합 미정 | Phase 4 어댑터 통합 경로 설계(v3 신규) |
| Comment 필터 | `ContentSafetyGuard.java` | Phase 6 comment 가이드 추가 예정 |
| Tell 후처리 | `OutputSanitizer.java` · `SelfCritiqueService.java` | Phase 2 T8-specific named-tell 라벨셋 추가 |

---

## Phase 요약

| Phase | 핵심 | GPU | 위치 |
|---|---|---|---|
| 0 | 창립·동결·kill criterion | 0 | 로컬 |
| 1 | Thin plaza 코퍼스 보강 (FRIEND/WORK 300→400+) | 중 | WSL 16 + 로컬 |
| 2 | Comment 품질 평가 하니스 (혼합 키트 설계) | 0 | 로컬+인간 |
| 3 | QLoRA SFT 데이터 준비 (≥500건 갈등 고품질 POST) | 0 | 로컬 8 |
| 4 | QLoRA 학습 + 어댑터 검증 (named-tell 분포) | **집약** | WSL 16 |
| 5 | Baseline 블라인드 (HARDER KIT, ≥4인) | 0 | 로컬+인간 |
| 6 | Comment 통합 + 최종 검증 | 0 | 로컬 8 |
| 7 | 클로즈아웃 + prod 배포/피벗 결정 | 0 | 로컬 |

---

## 연락처 & 권위본

- **오너**: justant (dalkong1030@gmail.com)
- **docs 권위본**: `docs/ai-user/history.md` (v3 항목 추가)
- **메모리**: `~/.claude/projects/.../memory/MEMORY.md` (v3 기록 누적)

---

**마지막 갱신**: 2026-06-22 Phase 0 준비

