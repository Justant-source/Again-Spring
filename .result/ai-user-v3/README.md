# AI-User v3 — 스타일 수렴 & 생성 품질 도약

> **상태**: 준비 중 (Phase 0)  
> **기간**: 2026-06-22 ~ (예상 7~10주)  
> **전임**: AI-User v2.1 (SHIPPED 2026-06-22, 5인 평가 28% PASS)

---

## 한줄 요약

v2.1에서 달성한 **카테고리 정렬의 tell 30% 자동 해결**에서 한 단계 더 나아가,  
**T8 비응집**(감정·사건 응집성) + **Thin plaza 보강**(FRIEND/WORK) + **Comment 품질 첫 측정**이라는  
3-트랙 강화로 AI 식별률을 **≤25%** (PASS)로 낮추는 것이 목표.

---

## v3가 왜 필요한가

### v2.1 성과 & 한계

**v2.1 Phase 8 블라인드 평가** (5인, 5/18=28% PASS):
- ✅ 카테고리 정렬이 맥락 불일치 tell 30% 자동 해결
- ✅ 신선 캐주얼 독자 기준 도입 = 게이트 신뢰성 극대화
- ❌ **T8(비응집)**: 감정과 사건 간 연결성 부족 → 구조적 모델 레벨 문제
- ❌ **Thin plaza**: FRIEND(165건)/WORK(156건) 코퍼스 부족 → weak spot
- ❌ **Comment**: POST만 평가 → 댓글 품질 미측정

**Post-ship 튜닝** (2026-06-22, commit 8c84b58f):
- T6 문법 개선: typoProb 상향
- T7 슬랭 추가: chosungInject
- T5 어휘 강화: 문어체 denylist 13종
- → 단문 단계적 개선이지만 **T8의 구조적 문제는 필터로 불가**

### v3 풀이 (3-트랙)

1. **T8 해소** — QLoRA fine-tuning
   - 갈등 커뮤니티 고품질 POST ≥500건으로 SFT
   - Haiku 4.5 → QLoRA (WSL RTX 3090)
   - 감정·사건 응집성 학습 (모델 레벨)

2. **Thin plaza 보강** — 코퍼스 3-4배 확대
   - FRIEND: 165 → 400+ (DCINSIDE·인스티즈·기타)
   - WORK: 156 → 400+ (CLIEN 직장인 게시판)
   - 제외: Blind.co (robots.txt 명시 차단)
   - 기존 v2.1 정화 기준 유지 (한글<10%, URL-heavy, dedup)

3. **Comment 품질** — 혼합 평가 첫 도입
   - POST 3 + COMMENT 3 = 혼합 키트
   - 신선 4인 평가 (v2.1 3인 강화)
   - 댓글의 자연스러움·맥락 이해도 측정

---

## Success Criterion

| 항목 | v2.1 기준 | v3 강화 |
|---|---|---|
| 타깃 독자 | 신선 3인 합의 | **신선 4인 합의** |
| 평가 단위 | 광장별 계정 타임라인 | + **댓글 섞임** |
| 코퍼스 | NATEPAN 중심 | + **FRIEND/WORK 3-4배** |
| Kill criterion | ≤60% 식별률 PASS | **≤25% 식별률 PASS** |
| 특화 보강 | 카테고리 정렬 | + **QLoRA T8** + **Comment** |

---

## 핵심 문서

| 파일 | 내용 |
|---|---|
| `charter-v3.md` | 프로젝트 charter (목표·kill criterion·규율 R1~R10) |
| `roadmap.md` | 상세 워크스트림 (Phase 0~7, 각 작업·gate·예상 기간) |
| `README.md` | 이 파일 (한줄 요약·문서 가이드) |
| `STATE.md` | 라이브 포인터 (현재 위치·진행률·다음 체크포인트) |
| `decisions.md` | 결정 로그 (V3-D01~D0X 의사결정 기록) |
| `steps/` | Phase별 상세 작업 지시 (생성 예정) |
| `crawl/` | Phase 1 크롤러 산출물 (정화 리포트 등) |
| `eval/` | 평가 결과 (Phase 2/5 블라인드, 분석) |

---

## 규율 (Rule) & Anti-pattern

### Core Disciplines (R1~R10)

**R1~R8**: v2.1 승계
- 단위 = 광장별 계정 타임라인
- Proxy 금지
- 변수 고정 (NATEPAN 전용)
- 저빈도 고정보 eval
- Kill criterion 사전 등록
- 판별기 = QA만 (rerank OFF)
- `AI_USER_ML_ENABLED=false` 영구
- Main 단일·docs-as-code·prod 게이트

**R9~R10**: v3 신규
- Thin plaza 수동 시드 (robots.txt 존중)
- Comment eval = 혼합 키트

### 절대 금지

❌ 오너 게이트 평가  
❌ Whack-a-mole (tell 계속 추격)  
❌ 새 크롤러 난발 (기존 5개 + 신규 4개만)  
❌ **QLoRA 데이터게이트 조건 미충족 발동** (3-AND 필수)  
❌ Comment 측정 스킵  
❌ Proxy/MAUVE/LLM-judge 부활

---

## 주요 의사결정 (Decision Nodes)

| 항목 | 현재 | 오너 확정 필요 |
|---|---|---|
| Kill criterion 임계값 | 제안: ≤25% | ✅ Phase 0 필수 |
| 평가자 수 | 제안: ≥4인 | ✅ Phase 0 필수 |
| HARDER KIT 구성 | 제안: AI 5 + Human 5 | ✅ Phase 5 필수 |
| FRIEND/WORK 광장 필수 | 제안: YES | ✅ Phase 0 권장 |
| Comment 포함 필수 | 확정: YES (R10) | — |

---

## 팀 & 연락처

- **오너 & 최종 판정**: justant (dalkong1030@gmail.com)
- **개발 리드**: Claude Code (로컬 + WSL Agent 조율)
- **평가자**: TBD (v2.1과 독립적인 신선 4인, Phase 0에 모집 시작)

---

## 다음 단계

1. ✅ `charter-v3.md` + `roadmap.md` + `README.md` 작성 완료
2. 📋 `STATE.md` + `decisions.md` 작성 (이어서)
3. 🔴 **오너 회신 대기** — kill criterion ≤25% 수치 확정
4. ⏳ Phase 0 완료 → Phase 1 시작 (Thin plaza 크롤)

---

**마지막 갱신**: 2026-06-22 Phase 0 준비

