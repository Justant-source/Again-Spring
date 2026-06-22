# AI-User v3 — Roadmap (상세 워크스트림)

> **프로젝트**: 스타일 수렴 & 생성 품질 도약 (T8 비응집·thin plaza·comment 품질)  
> **기간**: 2026-06-22 ~ (Phase 0~7 예상 7~10주)  
> **규율**: `charter-v3.md` R1~R10 + `README.md` 승계

---

## Phase 0 — 창립 & 동결 🔄

**목표**: v3를 독립 프로젝트로 창립. v2.1 post-ship 평가 + kill criterion + 고정 baseline 정의를 문서에 동결.

**완료 체크리스트**:
- [ ] `charter-v3.md` — v3 charter (목표·kill criterion·R1~R10)
- [ ] `README.md` — 요약 (이전: 1일 프로젝트 → v3는 7~10주 예상)
- [ ] `decisions.md` — V3-D01~V3-D03 초기화
- [ ] `STATE.md` — 라이브 포인터 초기화 (현재 위치: Phase 0 준비)
- [ ] `roadmap.md` — 이 문서
- [ ] **🔴 kill criterion ≤25% 오너 사전 등록** (Phase 0 완료 블로커)
- [ ] `npm run lint:docs` 통과
- [ ] `git commit && git push`
- [ ] v2.1 lessons.md 분석 → 적용항목 decisions.md에 V3-D04 등록

**v2.1 교훈 v3 적용** (from `ai-user-v2/lessons.md`):
- **L-P1-01** (Eval 오라클): v2.1은 신선 3인으로 충분했음 → v3는 4인 권장 (thin plaza 위험 증대)
- **L-P2-02** (카테고리 정렬): FRIEND/WORK가 weak spot 확인 → v3는 thin plaza 필수 포함
- **L-P3-01** (QLoRA 데이터게이트): v2.1은 비활성이었음 → v3는 처음 발동. 3조건 AND 게이트 적용
- **L-P5-01** (기준 설정): kill criterion 사전 등록이 감정 편향 차단 → v3도 동일
- **L-P8-04** (Blind.co): robots.txt 차단(ClaudeBot·anthropic-ai) → 존중 (thin plaza 크롤 제외)

**Gate**: 8개 문서 + kill criterion 타임스탬프 + `lint:docs` 통과 + git push

**예상 기간**: 1~2일 (문서 작성 + 오너 회신)

---

## Phase 1 — Thin Plaza 코퍼스 보강 🔜

**목표**: FRIEND(현 165건 → 목표 400+), WORK(현 156건 → 목표 400+) 코퍼스 3-4배 확대. 기존 v2.1 정화 기준 유지.

**동기**:
- v2.1 Phase 8 측정에서 ai-user-057 (FRIEND) 가장 많이 탐지 (2/4)
- FRIEND/WORK는 COUPLE/MARRIED/OTHER와 다른 주제·슬랭 패턴 → 코퍼스 부족 시 생성 품질 저하
- 현재 NATEPAN-only FRIEND/WORK는 중복·오염·얕이 → 다양한 소스에서 교차 수집 필수

### 1-A: 크롤러 신설 & 확대 (WSL 주도)

**신설 크롤러 목록** (기존 NATEPAN 유지 + 신규 4개):

| 크롤러 | 소스 | 주제 | 광장 | 코퍼스 목표 |
|---|---|---|---|---|
| NATEPAN (기존) | nate-pan.com/talk | 잡담·일상 | GENERAL | 유지 |
| NATEPAN-FRIEND | nate-pan.com/talk (섹션 필터) | 우정·인간관계 | FRIEND | +150→300 |
| DCINSIDE-MARRIED | dcinside.com (결혼 갤러리) | 부부·신혼 | MARRIED+COUPLE | +50 보강 |
| DCINSIDE-DATING | dcinside.com (연애 갤러리) | 연인 | COUPLE | +50 보강 |
| CLIEN-WORK | clien.net (직장인 게시판) | 직장 갈등 | WORK | +250→350 |
| INSTIZ-FRIEND | instiz.net (우정·인간관계) | 친구·사람관계 | FRIEND | +100→250 |

**제외 명시**: Blind.co (robots.txt에 ClaudeBot·anthropic-ai 명시 차단) — 자동화 크롤 불가, 수동 수집만 가능 (추후)

### 1-B: 크롤 파이프라인 (WSL Claude Code 16 에이전트)

**위치**: `ai-user/learning/app/crawlers/`

**작업스트림**:
1. **섹션 힌트 매핑** (`natepan.py` 기준)
   - NATEPAN 섹션 → FRIEND(새 필터) vs GENERAL(기존)
   - DCINSIDE 갤러리 → MARRIED/COUPLE 힌트 (근본 topic은 RAG에서 최종)
   - CLIEN 게시판 → WORK 힌트
   - 인스티즈 섹션 → FRIEND 힌트

2. **크롤 & 적재** (에이전트 병렬)
   - 각 크롤러마다 에이전트 1개 할당
   - rate limit: politeness + jitter (NATEPAN 기준 500ms/req 유지)
   - 출력: `crawl/phase1/raw/<source>_YYYYMMDD.jsonl` (raw 게시글)

3. **후처리** (순차, 단일 에이전트)
   - 통합 정화: 한글 비율 < 10% drop / URL-heavy drop / content_hash dedup
   - 정화 리포트: `crawl/phase1-report.md`
     ```
     | Source | Raw | Drop(Lang) | Drop(URL) | Drop(Dup) | Clean | Note |
     |---|---|---|---|---|---|---|
     | NATEPAN-FRIEND | 500 | 20 | 15 | 10 | 455 | … |
     | CLIEN-WORK | 450 | 10 | 25 | 5 | 410 | … |
     | … | … | … | … | … | … | … |
     ```

4. **임베딩 & 적재** (WSL RTX 3090 임베딩)
   - Clean 데이터 → KURE-v1 임베딩 (`EmbeddingService.embed_batch`)
   - `example_bank` (8099) + AI 유저 ML 판별기 (8201)
   - 메타: `plaza` (광장), `source` (크롤러 이름), `posted_at` (타임스탬프)

### 1-C: 코퍼스 검증 체크포인트

**Gate**:
- FRIEND clean 총 개수 ≥ 400건 (현 165 + 신규 235+)
- WORK clean 총 개수 ≥ 400건 (현 156 + 신규 244+)
- 기타 광장(COUPLE/MARRIED/OTHER) clean 이전 대비 ±20% (회귀 방지)
- 정화 drop 리포트 + before/after 스냅샷
- 임베딩 인덱스 재구성 완료 (8099/8201 sync)

**Halt**: 
- 크롤 차단/rate-limit 시 정중 백오프 + 오너 보고
- Clean 개수가 목표 50% 미만 시 → 소스 추가 또는 게이트 조정 협의

**예상 기간**: 3~5일 (크롤 + 정화 + 검증)

---

## Phase 2 — Comment 품질 평가 하니스 (Eval 설계) 🔜

**목표**: POST만 평가했던 v2.1 패턴을 버리고, **혼합 키트 설계** → comment 품질을 처음 측정.

**동기**:
- POST는 길이·구체성·슬랭이 많아서 tell이 명확함
- Comment는 단문 → tell이 희박 예상 → 별도 평가 데이터 필요
- v3 kill criterion에 comment가 포함되므로 Phase 2에서 측정 인프라 선구축

### 2-A: 블라인드 키트 v1 설계 (Post-Comment 혼합)

**키트 구성**:
- **AI 계정**: 5개 (각 기존 POST 3개 + 새 COMMENT 3개 = 18 unit)
  - 다양한 광장 커버 (FRIEND 1, WORK 1, COUPLE 1, MARRIED 1, OTHER 1)
  - thin plaza(FRIEND/WORK) 반드시 포함
- **Human 계정**: 5개 (실 NATEPAN, v2.1에서 검증된 고품질)
  - 동일하게 각 POST 3 + COMMENT 3 추출
- **총 18 unit** (AI 9+Human 9 이 아니라 AI 15+Human 15 = 30 unit)

**Comment 출처**:
- 실제 again-spring 플랫폼에서 AI가 생성한 댓글 (production 내역)
- 혹은 v2.1 평가 kit에서 AI 게시글에 달린 댓글 스크린샷

**평가자 지시문** (신규):
```
다음은 한 사용자가 다시봄에 올린 글과 댓글들입니다.
게시글 3개 + 댓글 3개를 모두 읽은 후,
"이 사용자는 실제 사람인가, 아니면 AI 봇인가?"
판단해 주세요.

- 글의 길이·말투·구체성·감정 변화
- 댓글의 반응 패턴·일관성·자연스러움
- 전체 타임라인의 일관성

등을 고려하면 됩니다.
```

### 2-B: 평가 실행 (로컬 + 평가자)

**평가자**: 신선 캐주얼 독자 4인 (E-013~E-016, 비v2.1 참여자)

**프로세스**:
1. 블라인드 키트 무작위 배치 (정답 봉인)
2. 평가자 각각 독립 채점 (post only 지시문과 유사 but comment 포함)
3. 계정별·광장별·AI vs Human 분류
4. 평가 리포트: `eval/phase2/comment-eval-v1.md`
   ```
   | Account ID | Plaza | Type | E-013 | E-014 | E-015 | E-016 | Consensus |
   |---|---|---|---|---|---|---|---|
   | ai-001 | FRIEND | AI | BOT | BOT | BOT | HUMAN | 3/4 BOT |
   | …
   ```

### 2-C: 검증

**Gate**:
- 평가 완료 (30 unit × 4 평가자 = 120 판정)
- Consensus 정의 (3/4 이상 동의 → 정답으로 간주)
- Inter-rater reliability (Fleiss' kappa ≥ 0.4 권장, 해석 필요)
- Comment-only 식별률 vs POST-only 식별률 비교 분석

**예상 기간**: 1~2주 (평가자 모집 + 채점 + 분석)

---

## Phase 3 — QLoRA SFT 데이터 준비 (T8 타깃) 🔜

**목표**: Haiku 모델에 fine-tune할 SFT 데이터셋 ≥500건 준비. T8(비응집) 해소를 위한 고품질 갈등 글만 선별.

**동기**:
- T8 = 감정과 사건 간 응집성 부족 (구조적 모델 레벨)
- 프롬프트·필터로 해결 불가 → fine-tuning으로만 개선 가능
- 데이터게이트: Phase 1(thin plaza) + Phase 2(comment eval) 완료 후 발동

### 3-A: 데이터 조건 (3-AND 게이트)

**데이터게이트 조건** (모두 만족해야 Phase 4 진행):
1. **Thin plaza 보강 완료** (Phase 1 gate 통과)
   - FRIEND ≥400, WORK ≥400
2. **잔존 tell이 T8에 귀속** (Phase 2 comment eval 분석)
   - Post-only 식별률 vs comment-mixed 식별률 비교
   - 차이 < 10pp → T8이 주요 원인 (데이터 준비 정당화)
3. **SFT 고품질 데이터 확보 ≥500건**
   - 소스: `example_bank`에서 검증된 **인간 글**만 선별 (source != 'SELF_GENERATED')
   - 선정 기준:
     - 구체적 사건 ≥1개 (trigger 필수 — "X가 Y를 했다" 형태)
     - 감정과 사건이 명확하게 응집 (후행 분석 필요)
     - 응집성 스코어 ≥0.7 (자동 NLP 점수, 수동 샘플 검증)

### 3-B: 데이터 추출 & 포맷팅 (로컬 8 에이전트)

**워크스트림**:
1. **후보 필터링** (WSL Claude Code)
   - `example_bank` 쿼리: `source != 'SELF_GENERATED' AND content_length ≥ 500 AND content_length ≤ 5000`
   - 캐주얼 커뮤니티만 (NATEPAN/THEQOO/CLIEN/DCINSIDE) → 필터 반영
   - 초기 후보: ~2000건

2. **응집성 점수화** (NLP + 수동 샘플)
   - 자동: 감정사 탐지 + 사건 구문 추출 + 거리·순서 점수화
   - 수동: 샘플 200건 → 점수 ≥0.7 기준선 재확인
   - 최종: 응집성 ≥0.7인 데이터만 선별 (목표 ≥500건)

3. **SFT 포맷 변환** (로컬)
   ```json
   {
     "instruction": "<persona_context>당신은 FRIEND 광장의 캐주얼한 사용자입니다. 친구와의 갈등을 풀어냅니다.</persona_context>",
     "input": "<context>context_block from similar examples</context>",
     "output": "<actual_human_post_content>"
   }
   ```
   - `instruction`: 광장별 persona 기본 설정
   - `input`: RAG 검색 결과 3-4개 예제 (v2.1과 동일)
   - `output`: 실제 인간 글 전문

4. **데이터셋 분할**
   - Train: 80% (400건)
   - Validation: 20% (100건)
   - Test: 별도 hold-out (20건, 최종 검증용)

### 3-C: 검증 체크포인트

**Gate**:
- SFT 데이터 Train/Val 확보 완료 (500건)
- 응집성 점수 방법론 문서화 + 수동 샘플 회귀 검증
- 데이터 전제 확인: trigger 포함률 ≥95%, 감정 언급률 ≥90%
- 광장별 분포 균형 (FRIEND/WORK ≥20%, 기타 ≤30%)

**Halt**: 
- 고품질 데이터 < 300건 시 → 선정 기준 완화 또는 Phase 4 연기 협의

**예상 기간**: 1~2주 (필터링 + 응집성 점수화 + 포맷팅)

---

## Phase 4 — QLoRA 학습 & 어댑터 검증 ⚡

**목표**: WSL RTX 3090에서 Haiku 4.5 QLoRA 어댑터 학습 + named-tell 분포 기반 검증. T8 감소 확인.

**환경**:
- **GPU**: WSL RTX 3090 (25.8GB VRAM)
- **프레임워크**: unsloth + trl (v0.7.10+)
- **모델 베이스**: `claude-haiku-4-5-20251001`
- **학습 방식**: LoRA rank=16, alpha=32 (typical)
- **배치**: 8 (VRAM 충분하면 상향)
- **에포크**: 3~5 (early stopping)

### 4-A: 학습 설정 & 실행 (WSL Claude Code)

**워크스트림**:
1. **Haiku 미세조정 스크립트 작성**
   - 위치: `ai-user/learning/app/qora_trainer.py` (신규)
   - 입력: Phase 3 SFT 데이터셋 (Train/Val)
   - 출력: 어댑터 가중치 + training_log.json
   ```python
   from unsloth import FastLanguageModel
   from trl import SFTTrainer
   from datasets import load_dataset
   
   model, tokenizer = FastLanguageModel.from_pretrained(
       model_name="claude-haiku-4-5-20251001",
       # (API 기반 모델이므로 실제는 프롬프트 포맷팅 수정 필요)
       load_in_4bit=True
   )
   
   dataset = load_dataset("json", data_files={
       "train": "data/train.jsonl",
       "validation": "data/val.jsonl"
   })
   
   trainer = SFTTrainer(
       model=model,
       tokenizer=tokenizer,
       train_dataset=dataset["train"],
       eval_dataset=dataset["validation"],
       args=TrainingArguments(
           output_dir="./adapters/haiku-v3-01",
           num_train_epochs=3,
           per_device_train_batch_size=8,
           per_device_eval_batch_size=8,
           learning_rate=2e-4,
           warmup_ratio=0.1,
           weight_decay=0.01,
           save_steps=50,
           eval_steps=50,
           logging_steps=10,
           bf16=True
       )
   )
   
   trainer.train()
   model.save_pretrained("./adapters/haiku-v3-01")
   tokenizer.save_pretrained("./adapters/haiku-v3-01")
   ```

2. **학습 실행** (WSL, 에이전트 1개)
   - 예상 시간: 4~8시간 (batch 8, epoch 3, 400건 데이터)
   - 모니터링: training_log.json (loss 수렴 확인)

3. **어댑터 저장 경로**
   ```
   ai-user/learning/adapters/haiku-v3-01/
   ├── adapter_config.json
   ├── adapter_model.bin
   ├── tokenizer_config.json
   ├── tokenizer.model
   └── training_log.json
   ```

### 4-B: 어댑터 검증 (명명된 tell 분포)

**검증 전략** (MAUVE 아님):
- **목표**: T8(비응집) 감소 확인 + 다른 tell 회귀 방지
- **방법**: named-tell 라벨셋을 Phase 2/3에서 미리 정의한 후, 어댑터 적용 생성물에 대해 라벨셋 재계산

**Named-Tell 라벨셋 정의** (Phase 3에서):
```yaml
T1_문법_사투리: ['~하쌔', '그쌈', '졸라', '뭐랄까']  # 기존
T2_슬랭_시대성: ['ㅇㅈ', '꿀잼', 'OMG', '어...', '헐']  # 기존
T3_어휘_이질: ['내적', '사명감', '진정성', '문학적']  # 기존
...
T8_비응집: ['사건나열미감정', '감정부재', '문장간결속력부족']  # 신규
```

**검증 프로세스**:
1. 어댑터 적용 전: 베이스 Haiku로 테스트 POST 20개 생성 → tell 라벨셋 계산
2. 어댑터 적용 후: QLoRA Haiku로 동일 테스트 POST 20개 생성 → tell 라벨셋 재계산
3. Δ 계산:
   ```
   | Tell | Base | QLoRA | Δ |
   |---|---|---|---|
   | T8_비응집 | 45% | 25% | -20pp ✓ |
   | T1~T7 | (각각) | (각각) | <±5pp ✓ |
   ```

### 4-C: Gate & Halt 조건

**Gate**:
- 학습 완료 + training_log.json 수렴 확인 (validation loss plateauing)
- T8 tell 개수 ≥15pp 감소 (Δ ≤ -15pp)
- T1~T7 회귀 없음 (각 Δ < ±5pp)
- 어댑터 저장 경로 확인 + 메타데이터(epoch/lr/batch_size) 기록

**Halt**:
- T8 감소 < 10pp → 데이터 품질 재검토 or 학습 조건 조정 후 재학습
- 다른 tell 회귀 >5pp → 정규화 상향 (weight_decay↑) 후 재학습

**예상 기간**: 1주 (학습 4-8h + 검증 1-2일)

---

## Phase 5 — Baseline 블라인드 (HARDER KIT) 🔜

**목표**: 강화된 v3 시스템(thin plaza+QLoRA+comment)을 처음 측정하는 블라인드. kill criterion 적용.

**HARDER KIT 정의**:
- **AI 계정**: 5개
  - FRIEND 1개 (Phase 1 약점)
  - WORK 1개 (Phase 1 약점)
  - COUPLE/MARRIED/OTHER 각 1개
  - 각 POST 3 + COMMENT 3 = 총 30 unit
- **Human 계정**: 5개 (v2.1과 동일 또는 신선 추가 가능)
  - 동일 구성 (각 POST 3 + COMMENT 3)
  - 총 30 unit
- **전체 키트**: AI 15 + Human 15 = 30 unit (v2.1 18과 확장)

### 5-A: 평가자 & 지시문

**평가자**: 신선 캐주얼 독자 **≥4인** (E-017~E-020)
- v2.1 평가자와 중복 금지
- 포렌식 배경 금지
- 봇헌터 경험 금지

**지시문** (Phase 2 기반, comment 포함):
```
다음은 다시봄 사용자들의 활동입니다. 글 3개 + 댓글 3개를 모두 읽고,
"이 사용자는 실제 사람인가, AI 봇인가?"
1~10점 확도와 함께 판단해 주세요.

고려사항:
- 글의 감정·구체성·일관성
- 댓글의 타이밍·반응성·맥락 이해
- 전체 계정의 인간미(자연스러움 vs 기계스러움)

등을 종합해 주세요.
```

### 5-B: 평가 실행 & 분석

**프로세스**:
1. 블라인드 키트 무작위 배치 (정답 봉인)
2. 평가자 각각 독립 채점 (1~10점 + 확도 + 코멘트)
3. 계정별 평균 점수 + consensus (threshold 설정: 예: ≥7 → BOT 판정)
4. Kill criterion 적용: **≤25% AI 식별률 = PASS**
   - AI 식별률 = (AI로 정답 판정된 계정 수) / (전체 AI 계정 5개)
   - 예: 1개만 봇으로 정답 → 1/5 = 20% PASS ✅
   - 2개 이상 봇으로 정답 → ≥2/5 = 40% FAIL ❌

5. **상세 분석**:
   - 광장별 식별률 (FRIEND/WORK vs 기타)
   - Comment-only 식별률 vs POST-only 식별률
   - Inter-rater 합의도 (Fleiss' kappa)
   - Named-tell 분포 (T1~T8 라벨셋 계산)

### 5-C: 결과 리포트

**파일**: `eval/phase5/baseline-result.md`

```markdown
# Baseline Evaluation (v3, HARDER KIT)

## Kill Criterion Status
- 목표: ≤25% AI 식별률
- 결과: XX% (Y/5 계정 봇 정답)
- **[PASS/FAIL]**

## 평가자 별 점수
| E-017 | E-018 | E-019 | E-020 | 평균 |
|---|---|---|---|---|
| … | … | … | … | … |

## 광장별 분석
- FRIEND: Z% 식별률
- WORK: Z% 식별률
- …

## Comment Effect
- POST-only: Z% (이전 측정)
- POST+COMMENT: Z% (현재)
- Δ: Z pp

## Named-Tell 분포
| Tell | 빈도 | T8 개선 | 회귀 |
|---|---|---|---|
| T1 | … | … | … |
| …
| T8 | … | ↓15pp ✓ | — |
```

**Gate**:
- AI 식별률 ≤25% → Phase 7 진행 (prod 배포 경로)
- AI 식별률 >25% → Phase 6B 고민 (QLoRA 재학습 or 추가 필터)

**예상 기간**: 1~2주 (평가자 모집 + 채점 + 분석)

---

## Phase 6 — Comment 통합 & 최종 검증 🔜

**목표**: comment 필터/가이드 통합 + 플랫폼 e2e 테스트. prod 배포 전 마지막 검증.

**Baseline 결과별 분기**:

### 6-A: PASS 경로 (AI 식별률 ≤25%)

1. **Comment 가이드 추가**
   - `ContentSafetyGuard.java` 또는 `OutputSanitizer.java`에 comment 특화 규칙 추가
   - 예: 댓글 길이 <100 char 시 → 문어체·사전형 제거 (문법적이 될 수 있음)
   - Phase 2 eval에서 발견된 comment-specific tell 반영

2. **E2E 테스트** (로컬 8 에이전트)
   - `frontend/tests/e2e-realbe/journeys/` 기존 spec 회귀 테스트
   - 신규: comment 생성 spec 추가 (ai-user 계정 → comment 생성 → 블라인드 평가자 검증)
   - 카테고리 정렬 회귀 테스트 (Phase 1 thin plaza 반영됨 확인)
   - QLoRA 어댑터 로드 성공 확인 (LlmWorkerPool 통합 경로)

3. **Prod 배포 준비**
   - `LlmWorkerPool` 어댑터 로드 경로 설계 (신규)
     ```java
     // LlmWorkerPool.java
     private LoraAdapter loadQLoraAdapterIfEnabled() {
         if (System.getenv("QORA_ADAPTER_PATH") != null) {
             return LoraAdapter.load(System.getenv("QORA_ADAPTER_PATH"));
         }
         return null;
     }
     ```
   - `.env.prod` 추가: `QORA_ADAPTER_PATH=/opt/again-spring/adapters/haiku-v3-01`
   - 어댑터 파일 prod 환경에 배포 (docker volume mount)
   - DB 백업 + dry-run

### 6-B: FAIL 경로 (AI 식별률 >25%)

1. **원인 분석**
   - T8 감소가 충분했는가? (Phase 4 검증 재확인)
   - Thin plaza 코퍼스 품질이 충분했는가? (Phase 1 정화 재평가)
   - Comment eval이 정말 문제인가? (Phase 2 결과 재분석)

2. **옵션 제시**
   - **옵션 A**: QLoRA 재학습 (데이터 확대 or 학습 조건 변경)
   - **옵션 B**: 추가 필터 (T8-specific post-processing)
   - **옵션 C**: 하이브리드 (QLoRA + Phase 2 결과 기반 comment 필터)
   - **옵션 D**: v3 배포 연기 (Phase 5 재측정 or 오너 결정 대기)

### 6-C: Gate

**Pass 경로**:
- E2E 테스트 전체 PASS
- `npm run lint:docs` 통과
- Comment 가이드 코드 리뷰 + 승인
- Prod 배포 준비 완료

**Fail 경로**:
- 옵션 A~D 중 오너 선택 + 결정 문서화

**예상 기간**: 1주 (경로 A) ~ 2-4주 (경로 B, 재학습)

---

## Phase 7 — 클로즈아웃 & Prod 배포/피벗 🎯

**목표**: v3 최종 마무리. PASS 경로 시 prod 배포 + 교훈 봉인. FAIL 경로 시 오너 결정 후 피벗.

### 7-A: PASS 경로 (Prod 배포)

**배포 프로세스** (CLAUDE.md 절대규칙 #4 따름):
1. ✅ Phase 5 블라인드 PASS (≤25% AI 식별률)
2. ✅ E2E-realbe 전체 통과 (dev:8090 대상, prod 대상 금지)
3. ✅ Main commit & push (`git log --oneline -3` 확인)
4. ✅ Prod 환경 DB 백업 (`mysqldump` or AWS snapshot)
5. ✅ Prod 배포
   ```bash
   cd env
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
   ```
6. ✅ Prod 스모크 테스트 (guest 계정, 광장별 타임라인 + comment 확인)
7. ✅ Prod 메트릭 모니터링 (LLM 비용, 오류율, 응답시간)

**결과 리포트**: `eval/phase7/prod-deployment-log.md`

### 7-B: 교훈 문서화

**파일**: `.result/ai-user-v3/lessons.md` (v2.1 lessons.md 패턴 따름)

내용:
- **L-P1-01**: Thin plaza 보강의 효과 (식별률 변화)
- **L-P2-01**: Comment eval의 영향도 (POST-only vs mixed)
- **L-P4-01**: QLoRA 어댑터의 성과 (T8 감소, 다른 tell 안정성)
- **L-P5-01**: HARDER KIT 설계 (4인 vs 3인 평가, FRIEND/WORK 필수)
- **L-P6-01**: LlmWorkerPool 통합 경로 (생산 코드)

### 7-C: FAIL 경로 (피벗 결정)

**오너 회의**:
- Phase 6B 옵션 A~D 중 선택
- 예상 일정 및 리소스 협의
- v3.1 또는 v4로 진행 여부 결정

**진행 결정**:
- **재학습** → Phase 4 재시작 (1주 소요)
- **추가 필터** → Phase 6C 옵션 B 구현 (3-5일)
- **하이브리드** → Phase 6C 옵션 C (1주)
- **연기** → v3 冬眠, 차기 결정 대기

### 7-D: 최종 State 갱신

**파일**: `STATE.md` 최종 갱신
```
최종 갱신: 2026-??-?? Phase 7
v3 상태: [SHIPPED / ABANDONED / REWORK]
Kill criterion: [PASS / FAIL]
이후 계획: [v3.1 planned / v4 planned / frozen]
```

**예상 기간**: 1주 (배포) ~ 4주 (재학습 재고)

---

## 실행 분배 테이블 (로컬 vs WSL)

| Phase | 주요 작업 | 위치 | 에이전트 수 | 소요 시간 |
|---|---|---|---|---|
| **0** | 문서 작성 + oowner 회신 | 로컬 | 1 | 1~2일 |
| **1** | 크롤 + 정화 + 임베딩 | WSL 16 + 로컬 | 16 | 3~5일 |
| **2** | Comment eval 하니스 설계 + 평가 | 로컬+인간 | 4 | 1~2주 |
| **3** | SFT 데이터 준비 | 로컬 8 | 8 | 1~2주 |
| **4** | QLoRA 학습 + 어댑터 검증 | WSL 16 | 1 (GPU) | 1주 |
| **5** | Baseline 블라인드 (≥4인) | 로컬+인간 | 4 | 1~2주 |
| **6** | Comment 통합 + E2E | 로컬 8 | 8 | 1주 (or 2~4주) |
| **7** | 클로즈아웃 + Prod | 로컬 | 1 | 1주 |
| **합계** | | | — | **7~10주** |

---

## 가드레일 & Anti-pattern

### v2.1에서 계승한 가드레일 (R1~R8)

✅ 보존:
- **R1**: 광장별 계정 타임라인 = 평가 단위 (v3는 thin plaza 강화)
- **R2**: Proxy 사다리 금지 (분포 sanity만 허용)
- **R3**: 변수 고정 (NATEPAN 전용, Phase별 변수 1개)
- **R4**: 저빈도 고정보 eval (baseline + 최종 2회)
- **R5**: Kill criterion 사전 등록 (오너 확정 필요)
- **R6**: 판별기 = QA만 (rerank OFF, D-108 유지)
- **R7**: `AI_USER_ML_ENABLED=false` 영구
- **R8**: Main 단일·docs-as-code·prod 게이트

### v3 신규 가드레일 (R9~R10)

🆕 추가:
- **R9**: Thin plaza 수동 시드 (robots.txt 차단 존중, Blind.co 제외)
- **R10**: Comment eval = 혼합 키트 (POST+COMMENT 섞음, 별도 지시문)

### v3 Anti-pattern (절대 금지)

❌ 절대 금지:
- 오너 게이트 평가 / whack-a-mole / thin plaza를 회피로 정당화
- 새 크롤러 난발 (기존 5개 + 신규 4개만, 이상 금지)
- **QLoRA 데이터게이트 조건 미충족 발동** (3-AND 모두 만족 필수)
- Comment 측정 스킵 (prod 출하 전 필수, Phase 2 완료 필요)
- Proxy/MAUVE/LLM-judge 부활 (v2.1에서 금지한 그대로)

---

## 의사결정 체크포인트 (Decision Node)

| 체크포인트 | 질문 | Phase | 담당자 | 결과 기록처 |
|---|---|---|---|---|
| **T0** | Kill criterion ≤25% 오너 확정? | 0 | 오너 | decisions.md V3-D03 |
| **T1** | Phase 1 코퍼스 FRIEND/WORK ≥400? | 1 | 개발자 | roadmap.md Phase 1 gate |
| **T2** | Phase 2 comment eval 분석 → T8이 주요? | 2 | 개발자+평가자 | decisions.md V3-D05 |
| **T3** | Phase 3 SFT 데이터 ≥500건 확보? | 3 | 개발자 | roadmap.md Phase 3 gate |
| **T4** | Phase 4 T8 개선 ≥15pp? | 4 | 개발자 | qora_trainer.py 로그 |
| **T5** | Phase 5 AI 식별률 ≤25%? | 5 | 평가자 | eval/phase5/baseline-result.md |
| **T6** | Phase 6 E2E PASS & 배포 준비 OK? | 6 | 개발자 | decisions.md V3-D06 |
| **T7** | Phase 7 Prod 배포 결정? | 7 | 오너 | STATE.md 최종 갱신 |

---

## 예상 일정 (Gantt)

```
Phase 0: |==|              (1~2일, 병렬 진행 가능 아님)
Phase 1:    |========|     (3~5일, WSL 크롤 16코어)
Phase 2:        |===========|  (1~2주, 평가자 모집 포함)
Phase 3:            |=========|  (1~2주, parallel to Phase 2)
Phase 4:                   |=======|  (1주, GPU 순차)
Phase 5:                      |===========|  (1~2주)
Phase 6:                          |===| or |===========| (1주 or 2~4주)
Phase 7:                             |===| (1주, 또는 피벗)
------
총 7~10주 (병렬 최대화 + 오너 응답 지연 포함)
```

---

**마지막 갱신**: 2026-06-22 Phase 0 준비

