# 연구문서 Phase 0–1 실행 항목 압축

> 원본: `Again-Spring/.request/ai-user/again-spring-ai-humanness-research-ko-v2.md`
> Phase 0–1만 추출. QLoRA/DPO는 미포함 (조건부 Phase 3).

## Phase 0 — 계측 먼저 (Step 3 구현)

**목표**: 판별기 없이 최적화 시작 금지. 베이스라인 먼저.

```python
# 판별기 구축 시 (Step 4)
from transformers import ElectraForSequenceClassification
model = ElectraForSequenceClassification.from_pretrained(
    "beomi/KcELECTRA-base", num_labels=2  # human=1, AI=0
)
# + KatFishNet 피처 (쉼표/띄어쓰기/품사 n-gram)를 LR로 결합
```

**평가 체계**:
- Level 1 (상시): MAUVE + 종결어미 JS발산 + 버스티니스 + 쉼표/띄어쓰기율
- Level 2 (핵심): KcELECTRA + KatFishNet 피처 → 커뮤니티별 AUC → 목표 0.5
- Level 3 (월 1회): 실제 사용자 블라인드 판별 (수동)

## Phase 1 — 프롬프트 엔지니어링 재구성 (Step 5~6 구현)

### 가장 급한 것: SelfCritique 개편 (Step 6)

| 현재 (문제) | 변경 방향 |
|---|---|
| 온점 전역 -2점 | 커뮤니티별 온점 실사용 비율 대조 |
| 단조 어미 패널티 | 종결어미 *분포* 대조 (음슴체는 원래 단조) |
| 쉼표 미측정 | **쉼표 빈도를 커뮤니티 실측치에 맞춤 ← 신규** |

### KatFishNet 3대 피처 (Step 1 구현)

| 피처 | 발견 | 강도 |
|---|---|---|
| **쉼표 사용 패턴** | LLM은 쉼표를 더 자주, 더 뒤에 사용 | 단일 최강 |
| **띄어쓰기 패턴** | 인간은 자주 틀림, LLM은 완벽 | 2위 |
| **품사 n-gram 다양성** | LLM이 정형 접속부사 반복 | 2~3위 |

참조 구현: github.com/Shinwoo-Park/katfishnet (피처 추출기만, 벤치 데이터 미사용)
⚠️ 반드시 AS 자체 크롤 코퍼스로 재학습. KatFish 벤치는 에세이/시 (커뮤니티 아님).

### Best-of-N 리랭킹 (Step 5 구현)

- N=4~8 초안 생성 → 판별기로 스코어링 → winner 선택
- 근거: Adversarial Paraphrasing (NeurIPS 2025) — 탐지율 평균 87%, Fast-DetectGPT 98% 감소
- AS 측 ActionExecutor 수정 지점: `executePost` 단일 `generatePost` 호출을 N번 + `/rerank` 호출로 대체
- 기존 `maxBigramJaccard` 반복가드·min-length는 winner 후처리 세이프티넷으로 유지

### 분포매칭 후처리 (Step 6, AS llm 측)

```yaml
# voices.yml에 신규 추가할 per-community 설정
post_processing:
  dcinside:
    comma_rate: 0.02        # 실측 분포 기반 (KatFishNet 대응)
    spacing_error_rate: 0.08
    typo_inject:
      - {from: "안 하고", to: "안하고", prob: 0.3}
    초성체_inject: [ㅇㅇ, ㄹㅇ, ㅅㅂ]
    sample_prob: 0.3
```

### 샘플링 파라미터 (Step 5, llm 설정)

- Temperature ~1.0 (현재 낮으면 탐지 쉬움)
- 반복 패널티 최소화 (현재 1.05도 인간 스팬 길이 4배 비자연화 위험)

## 완료 기준 (커뮤니티별)

- 판별기 AUC ≤ 0.55 → 해당 Voice 완성
- MAUVE ≥ 0.90 AND 종결어미 JS가 인간 저자 간 범위 내 → 배포 가능
