# LLM 호출 예산 (2026-08-18)

생성 **프롬프트 본문**(voice guide·reconstruct·JSON schema)은 그대로 둔다. 줄인 것은 **겹치는 품질 게이트**와 **불필요한 후속 호출**이다. 런타임 SSOT는 코드다. 이 문서는 호출 횟수·실패 시 동작을 운영자가 대조하기 위한 설명이다.

워커는 둘이다.

| 경로 | 컨테이너 | 누가 호출 |
|---|---|---|
| AI-user PLAN / legacy 생성 | `againspring-llm-ai-user` (`:8092`) | orchestrator |
| 마케팅 숏폼 훅·대본·시봄이 플랜 | `againspring-llm` (base, BE `RemoteLlmProvider`) | `VideoVariantService` 등 |

ASM/WaggleBot은 사연·variant 텍스트를 **다시 LLM으로 쓰지 않는다**. 렌더는 AS가 넘긴 brief를 합성한다.

---

## 1. 솔로 AI 글 (`generateAndHold`) — 글 1건

`StoryProfileAnalyzer`는 휴리스틱이며 LLM이 없다.

```
claim source (no LLM)
  → AI_POST micro-batch[0]     항상 1회 (본문 + 첫 댓글 슬라이스)
  → HUMAN_POST micro-batch[1+]  댓글 수 < ready-min-items(기본 6)일 때만
  → (llm-ai-user 내부) SelfCritique  결정론 FAIL일 때만 +1 (짧은 rewrite)
  → SoftProofread /generate/proofread  오탈자 휴리스틱일 때만 +1
```

발행 시점에 댓글이 READY 하한 미만이면 `persistAndFinalize`가 댓글만 `HUMAN_POST` **1회** 더 부를 수 있다(하한 미달이어도 글은 버린다).

### 1.1 예전 vs 지금 (토큰)

| 단계 | 2026-08-16 이전 | 지금 |
|---|---|---|
| micro-batch 슬라이스 | 활성 페르소나 전원(~150)을 5명씩 → 로그 `batches=30`. `remaining>0`(pool=16)이면 HUMAN_POST를 계속 시도 | matcher 상위 `max(batchSize, readyMinItems)+batchSize`명만 (기본 **11명**, 슬라이스 ≤3). 합친 댓글이 **6개 이상이면 후속 HUMAN_POST 생략** |
| 빈 `b1` | Codex/쿼터 실패 때도 거의 항상 1회는 호출 | 첫 배치가 이미 6개면 `b1` 없음. 미달이면 하한 또는 빈 응답까지 |
| SelfCritique 재시도 | 원본 thread-plan 프롬프트 전체(`<<<USER_PROMPT>>>`·소스·캐스트·스키마) + 초안 앞 400자 | **이슈 + 원문 전체 + 반말/존댓말 한 줄**. 생성 프롬프트 재첨부 없음. 90s. 실패 시 초안 유지 |
| proofread | 번들 성공 후 **항상** `/generate/proofread`. 줄 수 변경·504면 **hold 전체 폐기** | `SoftProofread.needsLlm` (예: `됬`, `되요`, 자모 연속)일 때만 호출. 실패·`PROOFREAD_STRUCTURE_CHANGED`·안전 실패면 **원문 유지 후 hold** |
| 소스 skip | — | 길이 1000자·특수문자로 claim을 막지 않음. 운영 실패는 proofread fail-closed·반말 위반·풀 빔이 원인 |

로그: `AI post micro-batch done ... llmCalls={} followUps={} plannedSlices={} items={}/{}`. `plannedSlices`는 캡된 캐스트 기준이며, 예전처럼 전원 슬라이스 수가 아니다.

### 1.2 코드

- 슬라이스 캡: `AiPostBundleService.capCommentersForMicroBatch`
- 후속 중단: 같은 클래스 `generateBundleMicroBatch` (`mergedItems >= readyMinItems`)
- 맞춤법: `SoftProofread` + `AiPostBundleService.proofreadBundle` + legacy `ActionExecutor.applySoftProofread`
- 비평 재시도: `SelfCritiqueService.buildRetryPrompt`

### 1.3 PLAN structured 내부 SelfCritique

`/v2/generate/thread-plan` 파싱 후 본문·댓글 각각 `critiqueAndRefine`을 탄다. `quickCheck` PASS면 **추가 CLI 호출 없음**. FAIL이면 항목당 짧은 rewrite 1회. 생성 JSON 스키마 호출과는 별개다.

---

## 2. 양면 사연

논리 단계는 그대로 **Call1 + Call2** (`PAIRED_PHASE1` / `PAIRED_PHASE2`). 캐스트가 크면 Call2만 댓글 후속으로 쪼갤 수 있다. 솔로 micro-batch 캡은 paired 전용 경로에 그대로 복사되지 않는다. 숏폼 variant는 글이 마케팅 슬롯에 들어갈 때 §3.

---

## 3. 마케팅 숏폼 (`VideoVariantService`) — AS BE → `againspring-llm`

시봄이 후보 리스트는 사연 LLM이 아니라 **키워드 스코어**다. 영상 직전만 채널별 LLM이다.

| 조건 | 호출 |
|---|---|
| 릴스만 / 쇼츠만 | 채널 **1회**. 대본 없음 또는 가드 후 플랜 < 최소(릴스 4 / 쇼츠 해당 min) 이고 상태가 `OK`/`PARSE_ERROR`이면 **보정 1회** |
| 릴스+쇼츠 | 채널당 위와 같음 → 보통 **2회**, 최악 **4회** (채널별 보정) |
| `MarketingLlmAuthGuard.isCircuitOpen()` | **0회**. 상태 `LLM_AUTH_CIRCUIT_OPEN` |
| `LLM_TRANSIENT_ERROR` (세션 한도·timeout 등) | 해당 채널 **보정 없음** (1회에서 끝) |
| `session limit` / `hit your session` | 인증 오류로 치지 않음. 회로를 열지 않음 |

프롬프트는 채널 전용 필드를 유지한다(훅·script 길이·시봄이 장수가 다름). 본문은 `BODY_PROMPT_MAX=900`자로 자른다. 30장 카탈로그 dump 금지.

ASM은 이 JSON brief를 받아 렌더만 한다.

---

## 4. 호출하지 않는 것 (오해 방지)

| 항목 | LLM? |
|---|---|
| `StoryProfile` / persona matcher | 아니오 |
| 시봄이 `sibom_candidates` | 아니오 (키워드) |
| 게시 후 조회수·좋아요·투표 리콘실 | 아니오 |
| 사람 글 후보 PLAN | `HUMAN_POST` 1회(+품질 게이트 시 댓글 1회) — 솔로 AI 글 micro-batch와 별개 |
| 30분 human-reply batch | chunk당 1회 (이번 토큰 작업 범위 밖) |

---

## 5. 운영 로그로 절감 확인

새벽 배치 후:

- orchestrator: `micro-batch done`의 `llmCalls`가 1~3인지, `proofread skipped`/`keeping original`인지
- `llm-ai-user`: `critique FAIL` 뒤 retry 프롬프트에 `<<<USER_PROMPT>>>`가 **없는지**
- 마케팅: 회로 open 시 `LLM_AUTH_CIRCUIT_OPEN`이고 `againspring-llm` invoke가 없는지

생성 품질 프롬프트(`voice/*.md`, PLAN schema) 변경 여부와 호출 **횟수**는 별개다.

---

## 6. LLM 관찰성 (Observability)

orchestrator는 모든 LLM 호출을 `[LLMSTATS]` 단일행 로그로 기록한다. 형식은 [`docs/ai-user/orchestrator.md` § admin/metrics](./orchestrator.md)를 참조.

**실시간 모니터링**:

```bash
# 예시: 마케팅 호출 중 실패율
docker logs againspring-ai-user-orchestrator | grep '\[LLMSTATS\].*type=VARIANT' | \
  jq -s 'group_by(.result) | map({result: .[0].result, count: length})'

# 토큰 비용 추정 (최근 24시간)
curl http://localhost:8096/admin/metrics/llm-today | \
  jq '.stats | to_entries | map({type: .key, tokens_in: .value.totalInputTokens, tokens_out: .value.totalOutputTokens})'
```

**대시보드 통합**: `/admin/metrics/llm-today` 엔드포인트를 Grafana/Datadog 등에 연동 가능(메모리 기반, 재시작 시 리셋).
