# Step 34 — M8: DCINSIDE 데이터 가용성 판정

**날짜**: 2026-06-16  
**결론**: DCINSIDE 학습 불가 — 장르 구조 불일치 (제외 결정)

---

## 배경

cond1 달성에 필요한 DCINSIDE n_human≥300. 현황: 39개(<<300).  
Plan: example_bank 336건 중 39개만 ML corpus에 있어서 cursor 리셋 재-pull로 복구 가능 예상(GT-D).  
→ **검증 후 계획 기각** — 장르 문제가 근본 원인.

---

## 실행 내용

### 1. cursor 리셋 + 재-pull

```bash
docker exec aiuser-ml-1 rm /app/data/.corpus_pull_cursor
docker compose restart aiuser-ml
# 로그: cursor=beginning, 0 inserted, 683 skipped (dup hash), 273 unknown source
```

결과: 0건 신규 인서트. 커서가 `2026-06-09 18:57:11`로 전진.

### 2. 수동 직접 인제스트

AS `http://100.81.189.92:8099/examples/export?community=DCINSIDE&sourceClass=human&limit=500&since=2000-01-01` 조회 → 264건 발견 → ML `/corpus/ingest` 직접 POST.

```
결과: inserted=0, skipped=264, filtered=0
```

264건 전부 기존 corpus_item 해시와 충돌 → 이미 존재.

### 3. DB 현황 확인

```sql
SELECT label, COUNT(*) FROM corpus_item WHERE community='DCINSIDE' GROUP BY label;
-- human: 39, ai: 230 → 전체 269건
```

### 4. 콘텐츠 장르 스팟체크 (human 5건 샘플)

| # | 콘텐츠 |
|---|---|
| 1 | 와인경진대회(Concours Mondial de Bruxelles) 소개 장문 |
| 2 | 핫셀 CFV100c 카메라 렌즈 후기 |
| 3 | 마쓰야마 3박4일 혼여 여행기 |
| 4 | 日 하마즈시 스시에 주방세제 사건 뉴스 |
| 5 | 림버스 메피스토펠레스 버스 수제작 과정 |

→ **전부 일반 DCINSIDE 갤러리 포스팅. 갈등 서사 0건.**

---

## 근본 원인 분석

**DCINSIDE 구조**: 주제별 갤러리(와인 갤, 카메라 갤, 게임 갤, 여행 갤...) 모음.  
"갈등 게시판"이 존재하지 않음.

**AS example_bank DCINSIDE 출처**:
- 39 human 항목 = 실제 DCINSIDE 갤러리 크롤링 (갈등 서사 없음)
- 225+ ai 항목 = 오케스트레이터가 DCINSIDE 포스팅으로 생성한 AI 텍스트
- 264개 직접 인제스트 실패 = AI corpus 해시와 모두 충돌 (오케스트레이터 출력물)

**결정적 불가 이유**:
- human corpus = 와인/카메라/여행 리뷰 (일반 관심사)
- ai corpus = 갈등 서사 (오케스트레이터 출력)
- 이 조합으로 학습하면 판별기가 "갈등 서사=AI" 학습 → **최악 역전 가속**

---

## 판정

| 항목 | 결과 |
|---|---|
| n_human 현황 | 39 (변동 없음) |
| 추가 확보 가능성 | **불가** — DCINSIDE에 갈등 서사 게시판 없음 |
| 학습 가능성 | **불가** — 장르 불일치로 역전 가속 위험 |
| cond1 달성 가능성 | 불가 |

**결론**: DCINSIDE = **학습 제외**. enable-gate cond1/cond2 산정에서 DCINSIDE 제외.

---

## 영향 (enable-gate 재정리)

| cond | DCINSIDE 제외 후 상태 |
|---|---|
| cond1 | ✅ THEQOO/CLIEN/NATEPAN 충족 (DCINSIDE 제외) |
| cond2 | ✅ THEQOO/CLIEN/NATEPAN AUC 신뢰 가능 |
| cond3 | ✅ |
| cond4 | ⚠️ UNVERIFIED (M1 재측정 진행 중) |
| cond5 | ❌ human_accuracy=1.0 (M7 후 개선 측정 필요) |

---

## 함정 (향후 조심)

- **DCINSIDE example_bank 재활용 유혹**: 264건이 많아 보여도 전부 AI 생성물 또는 비-갈등 장르.
- **cursor 리셋이 만능 아님**: cursor 리셋해도 장르 필터 없이는 쓸 수 없는 데이터.
- GT-D 예측("cursor 재셋으로 복구") 틀렸음 — 실제 근본 원인은 장르 구조 불일치.
