# Step 15 (T6) — 오프라인 A-B harness 실측 결과 (2026-06-16)

## 요약

THEQOO/CLIEN 각 10 컨텍스트 × 4 초안 → /eval/ab-test → MAUVE 비교.
**cond4 미충족.** THEQOO 역전 (Δ=-0.356), CLIEN 무신호 (Δ=0).

---

## 인프라

| 항목 | 값 |
|---|---|
| ML 서비스 | `100.115.252.61:8201` |
| 초안 생성 | `claude-haiku-4-5-20251001` via `claude -p` |
| 컨텍스트 | 고정 갈등 주제 10개 × 4 초안 |
| 드라이버 | `.result/ai-user/scripts/run_ab_test.py --workers 8` |
| pytest | 82/82 통과 (`-n auto` 멀티코어) |
| 테스트 패치 수정 | `app.storage.db.get_session` → `app.api.routes_eval/corpus.get_session` (모듈 캐싱 문제 수정) |

---

## 실측 결과

| 커뮤니티 | n_human | n_ai(POST) | n_contexts | MAUVE(rerank) | MAUVE(random) | **Δ** | degraded |
|---|---|---|---|---|---|---|---|
| THEQOO | 344 | 65 | 10 | 0.6292 | 0.9851 | **-0.356** | false |
| CLIEN | 294 | 40 | 10 | 0.9998 | 0.9998 | **0.000** | false |

WSL Job IDs:
- THEQOO: `01KV78DZWRSZKECCXYEAR0XH3Z`
- CLIEN: (DB EvalRun id 참조)

---

## 원인 진단

### THEQOO 역전 (Δ=-0.356)

**P(human) 역방향 문제**: /rerank가 "갈등 서사 중 가장 짧은/비서사적인 초안"을 선택.

```
/rerank 테스트 결과 (기준: 문체 다양도):
  "어제 남친이 약속 어겼어ㅠㅠ"       → P(human)=0.10 (최저!)
  "헐 ㄹㅇ 손절각ㅋㅋ 개공감"         → P(human)=0.39
  "작성자님 입장에서는..."(격식체)      → P(human)=0.92 (최고!)
  "당신의 남자친구는..."(AI 분석조)    → P(human)=0.75
```

**근본원인**: THEQOO 인간 코퍼스에 링크포스트·공지·짧은 반응이 혼입.
- 인간 corpus 샘플: `"https://x.com/..."`, `"안녕하세요. 기술관리자입니다."`, `"뭐냐고.....!!!"`
- AI corpus 샘플: 전부 구조화된 갈등 사연 (`"단톡에 내 얘기가 올라와 있었음\n며칠 전에..."`)
- → 판별기 학습: "갈등 사연 = AI", "링크/공지/짧은 반응 = 인간"

### CLIEN 무신호 (Δ=0)

- CLIEN AI 생성이 이미 인간 분포와 매우 유사 (MAUVE=0.9998)
- 선택의 여지 없음 — rerank와 random 모두 동일한 고품질 텍스트 선택

---

## 긍정 발견

**run_ab_test.py 단순 프롬프트 초안의 MAUVE=0.985** (random 선택 기준):
- 오케스트레이터 실제 봇 코퍼스 MAUVE=0.345 vs 단순 프롬프트 MAUVE=0.985
- **함의**: 오케스트레이터의 posts가 THEQOO 스타일과 멀리 있음.
  T8 TSD 프롬프팅이 해결해야 할 핵심 문제.

---

## 후속 조치 (Phase C)

1. **T8 (Step 17)**: PromptAssembler THEQOO 시스템 프롬프트에 TSD 추가
   - 목표: 오케스트레이터 MAUVE 0.345 → 0.60+
   - 초안 생성 단순화: 긴 구조화 서사 → 자연스러운 구어체 갈등 표현

2. **THEQOO 코퍼스 정제**: 인간 corpus에서 링크/공지 필터링 후 재학습

3. **T5 (Step 16)**: n_ai → 100 (THEQOO=65, 35개 더 필요)

4. **재측정**: T5+T8+코퍼스 정제 후 A-B 재실행 → cond4 재검증

---

## 인프라 최적화 (2026-06-16)

- `pytest-xdist` 추가 → `pytest -n auto` 멀티코어 (20 CPUs, 최대 16 사용)
- `LogisticRegression(n_jobs=-1)`, `cross_val_score(n_jobs=-1)` → sklearn 멀티코어
- `run_ab_test.py --workers 8` → ThreadPoolExecutor 병렬 초안 생성 (40호출/~30초)
