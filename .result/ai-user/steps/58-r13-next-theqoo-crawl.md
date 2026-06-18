# Step 58 (R13-next) — THEQOO 크롤링 실행 준비

## 상태

- 선택지: **C) 크롤링**
- 실행 형태: **최대 8-way 병렬 shard**
- 목적: `source="theqoo"` human corpus를 300+까지 증설해 Step 58 재학습 입력으로 사용

---

## 이번 세션 변경

### 1. 크롤러 정합성 보정

- 파일: `.result/ai-user/scripts/crawl_theqoo.py`
- 변경:
  - `source` 기본값을 `theqoo_crawl` → **`theqoo`** 로 변경
  - 이유: Step 55/R13 검증은 `source_filter="theqoo"`를 사용하므로, 새 수집분도 같은 source로 들어가야 Δ_real 재검증에 반영됨
  - API 토큰/엔드포인트/필터 임계값을 env로 오버라이드 가능하게 유지
  - 본문 추출 패턴을 `articleBody` 단일 의존에서 다중 패턴으로 완화

### 2. 8-way 병렬 런처 추가

- 파일: `.result/ai-user/scripts/run_theqoo_parallel.sh`
- 역할:
  - `THEQOO_WORKERS=8` 기준 shard fan-out
  - board + page range를 분할해 병렬 실행
  - shard별 로그를 `.result/ai-user/logs/theqoo-crawl/` 아래에 저장

### 3. Codex-only 평가 경로로 전환

- 파일: `.result/ai-user/scripts/run_ab_test.py`
- 변경:
  - clcocloud API 경로 비활성
  - `codex exec` 단일 경로로 초안 생성
  - 8-worker 병렬 생성 확인 완료

---

## 1차 실행 결과 (2026-06-18)

### 8-way crawl batch #1

- boards: `square hot ktalk beauty`
- pages:
  - round1 = 1-4
  - round2 = 5-8
- 결과:
  - inserted = **31**
  - skipped = 45
  - filtered = 296

### corpus / train

- `/corpus/stats`:
  - THEQOO human = **374**
  - THEQOO ai = **116**
- `/train` (THEQOO only):
  - version = `01KVDHM4VR3EGEZYMN7D4SYSWZ`
  - CV-AUC = **0.9958**
  - n_human = 374
  - n_ai = 100

### P(human) 스팟체크

| text | humanProb | 메모 |
|---|---|---|
| slang-human | 0.5596 | 이전보다 상승했지만 아직 충분히 높진 않음 |
| formal-ai | 0.9277 | 여전히 과대평가 |
| narrative-ai | 0.9962 | 여전히 과대평가 |

### Codex-only Δ_real 재측정

- command path: `run_ab_test.py` → `codex exec` only
- source_filter = `theqoo`
- n_contexts = 12
- snapshot_size = **142**
- mauve_rerank = **0.9907**
- mauve_random_mean = **0.8510**
- delta = **+0.1397**

해석:
- **좋은 신호**: Δ_real이 음수에서 **양수로 회복**
- **미완료**: Step 58의 원래 목표인 `real theqoo human >= 300`은 아직 미달
  - 현재 real-only snapshot = **142**
  - 이유: 기존 THEQOO human 374 중 다수는 진짜 더쿠 source가 아니라 과거 소스

---

## 현재 판정

- **선택지 C는 유효**: 크롤링으로 real corpus 방향 교정이 가능하다는 신호 확보
- **cond4 방향은 회복**: `Δ_real > 0`
- **Step 58 완료 아님**: real-only source corpus가 아직 300 미만
- **즉시 다음 작업**: 2차/3차 crawl batch로 `source=theqoo` snapshot 300+까지 증설

---

## 후속 배치 결과

### 2차 batch — 기존 보드 deeper window (p9-16)

- boards: `square hot ktalk beauty`
- 결과:
  - inserted = **2**
  - skipped = 30
  - filtered = 232
- 해석:
  - 기존 보드의 deeper page는 거의 소진
  - `ktalk`만 미세하게 추가

### 3차 탐색 — 대체 보드

- `love`: 목록은 열리지만 상세 본문이 연속 **403**
- `talk`: 상세 본문이 연속 **403**
- `job`: page1에서 수집 가능성 확인

### 4차 batch — `job` 집중 수집

- boards: `job`
- shard: `p1-2`, `p3-4`, …, `p15-16`
- 결과:
  - inserted = **10**
  - skipped = 18
  - filtered = 108
- 핵심:
  - 실질적 수확은 `job p1-2` shard에 집중
  - `p3-16`은 거의 소진

### 최신 corpus 상태

- `/corpus/stats`:
  - THEQOO human = **386**
  - THEQOO ai = **116**
- real-only snapshot 추정:
  - 기존 142 + deeper 2 + job 10 = **약 154**

### 다음 최적화 방향

1. `job p1-2` 주변의 헤더/세션 전략 조정
2. 상세 403이 나는 `love/talk` 보드 우회 가능성 점검
3. real-only snapshot 300+ 도달 후 `source_filter=theqoo` 재측정 반복

---

## 기본 실행 예시

```bash
cd /home/justant/Data/Again-Spring
chmod +x .result/ai-user/scripts/run_theqoo_parallel.sh
THEQOO_ML_API_KEY=... \
THEQOO_WORKERS=8 \
THEQOO_PAGES_PER_SHARD=4 \
THEQOO_BOARDS="square hot ktalk beauty" \
.result/ai-user/scripts/run_theqoo_parallel.sh
```

### 드라이런

```bash
THEQOO_DRY_RUN=1 .result/ai-user/scripts/run_theqoo_parallel.sh
```

---

## shard 분할 규칙

- worker 0..7
- 각 worker는 한 board와 `page_start + round * pages_per_shard` 범위를 담당
- board 간에는 라운드로빈으로 분산
- 기본값:
  - boards = `square hot ktalk beauty`
  - workers = `8`
  - pages_per_shard = `4`
  - 결과적으로 첫 실행은 최대 8 shard를 만들고, board별 초기 page window를 넓게 훑음

예시:

| shard | board | pages |
|---|---|---|
| w00 | square | 1-4 |
| w01 | hot | 1-4 |
| w02 | ktalk | 1-4 |
| w03 | beauty | 1-4 |
| w04 | square | 5-8 |
| w05 | hot | 5-8 |
| w06 | ktalk | 5-8 |
| w07 | beauty | 5-8 |

실행 환경에서 `THEQOO_BOARDS`를 조정하면 보드 분포를 바꿀 수 있다.

---

## 주의

1. 법적/운영 리스크 검토 전 장시간 대량 실행은 보수적으로 운영.
2. `DELAY` 기본값은 1.2초로 유지. 과도한 병렬 확대 금지.
3. 수집 후엔 반드시:
   - `n_theqoo >= 300` 확인
   - 재학습
   - `source_filter="theqoo"`로 Δ_real 재측정
   - THEQOO h2h survey 재생성

---

## 다음 즉시 작업

1. WSL/ML 접속 경로에서 8-way 병렬 실행
2. inserted/skipped/filtered 집계
3. 수집량이 부족하면 board window 재조정 후 2차 실행
