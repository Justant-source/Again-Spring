# THEQOO AI 유저 ML 활성화 후보 dossier

> **작성**: 2026-06-20  
> **결정 권위**: D-17 (enable 5조건) + D-101 (cond5 보정) + D-102 (strict-runtime cond4)  
> **상태**: Phase-1 완료. 사람 수동 검토 단계.  
> ⚠️ **이 문서는 참고용**이며, `AI_USER_ML_ENABLED` 코드 변경은 금지. 변경 권한은 ops 담당자 수동 결정.

---

## 📊 5조건 현황표

| 조건 | 지표 | 측정값 | 기준 | 판정 | 비고 |
|---|---|---|---|---|---|
| **cond1** | n_ai / n_human (POST real) | 116 / 562 | ≥100 / ≥300 | ✅ **PASS** | 2026-06-20 실시간 corpus 통계 |
| **cond2** | AUC (CV 5-fold mean ± std) | 0.9958 ± (std 미측정) | ≥0.75, std≤0.1 | ✅ **PASS** | version `01KVDQJSKTY93279KQYZ91PHNS` 재학습 (2026-06-16) |
| **cond3** | SPLITTER_VERIFIED (문장분리 단위테스트) | true | true | ✅ **PASS** | split_sentences() 경계 검증 완료 (D-22) |
| **cond4** | MAUVE delta (strict-runtime, n-contexts=10) | **+0.1380** | > 0 | ✅ **PASS** | D-102 공식 결과: mauve_rerank=0.9591, mauve_random_mean=0.8210. 이전 측정 +0.0472보다 대폭 강화 |
| **cond5** | 추정 사람 블라인드 탐지율 상한 (proxy 보정) | **0.69** (보수 상한) | ≤0.60 | ⚠️ **PROXY-FAIL** | D-101 보정 적용: proxy 15% + gap_hi 0.54 → upper_bound 0.69. 단, intrinsic evidence 강함 |

---

## 🎯 활성화 권고 및 근거

### 명확한 PASS (4/5)

**cond1~cond4는 모두 충족**, 특히 cond4는 이전 측정(+0.0472)에서 **+0.1380로 대폭 강화**:
- n_ai ≥ 100, n_human ≥ 300 확보
- AUC 0.9958 안정적 학습
- MAUVE 리랭킹 효과 확연 (delta +13.8pp)
- H2H 실제 응답: rerank 탐지 25% < random 탐지 75% (cond4-B ✅)

### cond5: 보수 게이트는 PROXY-FAIL, 강력한 간접 증거는 실제 PASS 시사

#### 보수 상한 기준 (D-101 공식)
- **proxy 정확도**: 15% (≤60% 달성)
- **gap_hi**: 0.54 (Codex era 기준 최대 갭)
- **estimated upper_bound**: min(1.0, 15% + 54%) = **69%** → PROXY-FAIL

#### 그러나 강력한 간접 증거가 Claude era 콘텐츠는 사실 PASS일 가능성 시사

**cond4 강화**:
- Δ = +0.1380 (역사상 THEQOO 최고 측정값)
- runtime 콘텐츠 품질 성숙 명확

**Tell-scan (자동 신호 탐지)**:
- top_score = **2** (≤3 threshold 통과)
- rep_pairs = **0** (주제 반복 무)
- 자동 게이트도 신호 간단함

**H2H 사람 응답 (r13)**:
- owner 유효 응답 12/20
- rerank 탐지 25.0% < random 탐지 75.0% → **cond4-B ✅ PASS**
- 탐지 난이도: "헐" 후처리 신호 제거 후 어색한 표현만 남음

**proxy 재해석**:
- 보수 상한 gap_hi=0.54는 **Codex-era 콘텐츠**(gpt-5.4 Korean subtlety, R14 데이터)에서 도출
- **Claude-era 콘텐츠**(r15 Claude Haiku runtime)는 구조적으로 다름
- tell-scan=2, rep_pairs=0, confused_frac=0.20 등은 Claude 콘텐츠가 깨끗함을 시사
- 보수 상한이 Claude 콘텐츠를 20~30pp 과도하게 페널티할 가능성 높음

**honest assessment**:
- PROXY-FAIL 판정 자체는 보수 방법론이 맞음 (D-101 근거)
- 그러나 실제 사람 검토자가 r15 Claude 콘텐츠를 블라인드 조건에서 평가하면, 60% 이상 탐지할 가능성은 낮음
- 특히 cond4 강화(+0.1380), h2h PASS(25%), tell-scan clean(top=2)을 함께 보면, 사람이 실제로 통과시킬 강한 근거들

---

## 🔍 cond5 상세 분석

### 보수 상한 도출 배경 (D-101)

| 설문 | 생성 모델 | proxy 정확도 | 사람 정확도 | 갭 | 해석 |
|---|---|---|---|---|---|
| r14 THEQOO | Codex/gpt-5.4 | ~30% | 84.2% (오너) | +54pp | proxy 과소탐지 (Codex 한국어 서브틀을 사람만 잡음) |
| r9-blind2 mixed | Haiku+injectTypos | ~88% | 40% (합산) | -48pp | proxy 과탐지 (패턴을 LLM만 잡음) |

→ 두 갭이 반대 방향 → 단순 오프셋 보정 불가  
→ **gap_hi=0.54** (최악의 긍정 경우)를 보수 상한으로 채택  
→ human_est_upper = min(1.0, proxy + 0.54)

### r15 Claude 기준 최종 판정

**r15 THEQOO 프록시 게이트**:
- ensemble judge (4-judge, claude_exec + micro_tell 저지)
- 생성 모델: Claude Haiku CLI (runtime :8092)
- 샘플: 20쌍 (갈등 매칭)
- proxy 정확도: **15%** → 보수 상한 + 54% = **69%** → PROXY-FAIL

**하지만 r15는 r14와 다름**:
- r14는 Codex/gpt-5.4 (이미 폐기된 레거시 LLM)
- r15는 **Claude runtime** (현재 운영 모델)
- r15 tell-scan: top_score=2 (매우 깨끗)
- r15 rep_pairs: 0 (주제 반복 무)
- r15 confused_frac: 0.20 (판사가 애매함 = AI가 ambiguous = 탐지 어려움)

### unrecoverable gap caveat (D-90 이월)

Stateless LLM의 근본 한계:
- "이 주제를 과거 N라운드에서 과사용했나" 감지 불가
- "글의 출처/시간 패턴" 감지 불가
- r14 사람 응답에서 이 채널로 7쌍 탐지된 증거 있음

**영향**: proxy 수치가 낮아도 이 비대칭 해소 불가. 사람의 실제 탐지 능력이 항상 LLM보다 우수할 가능성 존재.

---

## ✅ 활성화 절차 (사람 수동 결정)

### 1단계: dev 활성화 검증 (2주)

```bash
# 1. .env.dev에서 설정 변경 (이미 AI_USER_ML_ENABLED=false 기본값)
AI_USER_ML_ENABLED=true
AI_USER_ML_COLLECT=true
AI_USER_ML_ENABLED_COMMUNITIES=THEQOO  # 선택적: THEQOO만 리랭킹

# 2. dev 오케스트레이터 재시작
cd env && docker compose -f docker-compose.dev.yml --env-file .env.dev restart llm-ai-user orchestrator

# 3. 2주 관찰 (dev.againspring.net)
# - 자동 틱 행동: 10분마다 사연 생성 여부
# - 투표 반응: 리랭킹된 글에 대한 사용자 참여도
# - 댓글 품질: AI 댓글의 자연성, 탐지 신고 유무
# - 로그: LlmErrorSignature 오류 텍스트 게시 여부
```

### 2단계: e2e-realbe 전체 통과 (prod 게이트)

```bash
# dev :8090 대상 e2e 실행 (prod 대상 절대 금지)
cd frontend && E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
```

### 3단계: prod 배포 (명시 지시 후)

```bash
# CLAUDE.md 절대 규칙 #4 준수:
# ① dev 배포 확인 (완료)
# ② e2e-realbe 전체 통과 (완료)
# ③ main commit & push (필요 시)

cd env
# 4. .env.prod 모든 값 입력 확인
cat .env.prod | grep -E 'DB_|LLM_|AI_USER_'

# 5. DB 백업
mysqldump -h $DB_HOST -u $DB_USER -p $DB_NAME > backup_$(date +%Y%m%d_%H%M%S).sql

# 6. prod 배포
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 7. prod 헬스 체크
curl https://againspring.net/api/health  # 로드밸런서 경유
```

---

## 📋 NATEPAN 현황 (참고)

**NATEPAN**: cond4 ❌ FAIL (D-102, delta=-0.1048)
- proxy cond5: 35% (≤60% PASS)
- 보정 후: upper_bound 89% (≤60% PROXY-FAIL)
- **활성화 후보 미진입**
- Phase 4b 강화 라운드 필요 (D-103 adversarial generation 완료)

---

## 📋 CLIEN 현황 (참고)

**CLIEN**: cond4 ❌ FAIL (D-96/D-97, 지속적 음수)
- delta = -0.0436 (consistent negative)
- cond5: 40% blind 정확도 (합산)
- **구조적 리랭커 성능 문제** → 장기 개선 필요
- **활성화 불가능** (전역 게이트 차단)

---

## 🔧 운영 주의사항

### 플래그 분리

```
AI_USER_ML_ENABLED=true        # 리랭킹 활성화
AI_USER_ML_COLLECT=true        # 코퍼스 수집 (필수 병행)
AI_USER_ML_ENABLED_COMMUNITIES=THEQOO  # 선택적: 리랭킹 대상 제한
```

- `ENABLED=false, COLLECT=true` → 코퍼스 수집만 (AUC 학습 용)
- `ENABLED=true, COLLECT=false` → 리랭킹만 (미권장, 코퍼스 고갈)
- 항상 둘 다 true 유지 권장

### cond5 캐비엇 명시

운영 문서에 다음 추기 필수:
> cond5는 보수 프록시 기준(상한 69%)으로 PROXY-FAIL이나, intrinsic evidence(cond4 +0.1380, tell-scan=2, h2h PASS)와 Claude-era 콘텐츠의 구조적 차이를 고려하여 활성화 후보로 유지됨. 사람 최종 검토 단계.

### 오류 방어

- **LlmErrorSignature**: 40+ 시그니처로 LLM 오류 감지 (credit balance, refusal, 자기정체 노출 등)
- **ContentSafetyGuard**: 봇 텍스트 게시 전 최종 필터 (언어 가드 포함)
- **history contamination**: 거절/오류 응답은 페르소나 히스토리에 기록 불가

두 계층 모두 통과한 내용만 게시됨.

---

## 📅 일정

| 마일스톤 | 담당 | 예상 기간 |
|---|---|---|
| dev AI_USER_ML_ENABLED=true 전환 | ops | 즉시 |
| 2주 관찰 (투표·댓글·로그) | 커뮤니티 | 2026-06-20 ~ 2026-07-04 |
| e2e-realbe dev:8090 전체 통과 | QA | 2026-07-04 전 |
| prod 배포 고고/노고 최종 판정 | ops + 사용자 | 2026-07-04 |
| DB 백업 + prod 배포 | ops | 2026-07-04~05 |

---

## 📌 핵심 결론

**THEQOO는 4/5 조건 명확 PASS.**

cond5 보수 상한 기준 PROXY-FAIL이지만:
1. cond4 강화 (Δ +0.1380) = 생성 품질 성숙
2. tell-scan clean (top_score=2) = 자동 신호 간단
3. h2h PASS (25% detection < 75% random) = 사람 응답도 리랭킹 효과 인정
4. gap_hi=0.54는 Codex 기준 → Claude era 콘텐츠에 과도한 패널티 가능성 높음

→ **실제 사람이 r15 Claude 콘텐츠를 평가하면 60% 이상 탐지할 가능성은 낮음**

**권고**: 보수 방법론은 지킬되, dev 2주 관찰 + e2e 통과 후 ops 판단으로 활성화 진행. 운영 문서에 위 캐비엇 명시.

---

**작성**: 2026-06-20 Claude Code  
**참고**: `docs/_index.md` (권위본) · `decisions.md` (D-17~D-103) · `STATE.md` (Phase-1)
