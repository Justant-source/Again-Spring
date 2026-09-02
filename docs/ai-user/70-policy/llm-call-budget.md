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

**구조적 AI투 후보 로깅 (2026-09-02, 점수 미반영)**: `quickCheck`가 마지막 문단 요약·"아니라" 대칭 대조 반복(`SelfCritiqueService.hasClosingSummaryParagraph`/`countSymmetricContrast`)을 감지하면 `log.debug`로만 남긴다. score·passed·LLM 호출 횟수에는 영향 없음 — 로컬 블라인드 코퍼스에 AI/사람 라벨이 없어 사전 캘리브레이션이 불가능해 실제 생성물 로그로 데이터를 모으는 단계다. `buildRetryPrompt`의 재시도 프롬프트에는 이 두 패턴을 포함한 구조적 체크리스트를 항상 덧붙인다(신규 호출 없음, 기존 재시도 지시문에 한 줄 추가).

### 1.4 CLI 도구 오버헤드 + 프롬프트 지시 JSON 모드 (2026-08-21~22)

`claude -p` 호출은 매번 Claude Code CLI의 도구 정의 전체를 프롬프트에 실어 보낸다 — 앱 프롬프트 크기와 무관한 고정 부담(빈 호출 기준 ~25k 토큰). `--disallowedTools "*"`로 대부분 제거 가능하지만, `--json-schema`를 쓰는 구조화 호출은 `StructuredOutput` 도구를 살려둬야 해서(`"*"`를 쓰면 스키마 강제가 깨짐) 명시 disallow 목록만 적용 가능 — 그래도 ~18.8k 토큰이 남는다.

`LLM_STRUCTURED_PROMPT_MODE=true`(`.env.ai-user`에서 활성)는 `--json-schema` 대신 스키마를 프롬프트 지시로 주입하고 `"*"`를 적용해 이 잔여 오버헤드도 없앤다. 스키마 강제가 사라지므로 관대한 JSON 추출기(직접 파싱 → 코드펜스 제거 → 첫 `{`~마지막 `}` 추출)가 필요하다.

**dev 실측** (`/v2/generate/thread-plan`, 동일 요청):

| | 입력 | 출력 | 소요 |
|---|---|---|---|
| 스키마 모드(캐시 warm) | 49,311 | 3,888 | 43.3s |
| 프롬프트 모드(캐시 cold) | 4,381 | 859 | 13.9s |

캐시가 걸린 스키마 모드보다 캐시 없는 프롬프트 모드가 11배 작다 — 그동안 캐시에 얹혀 있던 4만 토큰대가 앱 프롬프트가 아니라 CLI 도구 정의였다는 증거. 파스 실패 시 스키마 강제가 없어 글+댓글 번들 전체가 유실되므로 실패율은 `[LLMSTATS] retryReason=PARSE_FAIL` + §6 서킷브레이커·§Phase4 텔레그램 알림으로 감시한다.

롤백: `LLM_STRUCTURED_PROMPT_MODE=false` + 워커(`llm-ai-user`) 재빌드·재기동.

---

## 2. 양면 사연

논리 단계는 그대로 **Call1 + Call2** (`PAIRED_PHASE1` / `PAIRED_PHASE2`). 캐스트가 크면 Call2만 댓글 후속으로 쪼갤 수 있다. 솔로 micro-batch 캡은 paired 전용 경로에 그대로 복사되지 않는다. 숏폼 variant는 글이 마케팅 슬롯에 들어갈 때 §3.

---

## 3. 마케팅 숏폼 (`VideoVariantService`) — AS BE → `againspring-llm`

시봄이 후보 리스트는 사연 LLM이 아니라 **키워드 스코어**다. 영상 직전만 채널별 LLM이다.

### 3.1 호출 사례

| 조건 | 호출 |
|---|---|
| 릴스만 / 쇼츠만 | 채널 **1회**. 대본 없음 또는 가드 후 플랜 < 최소(릴스 4 / 쇼츠 해당 min) 이고 상태가 `OK`/`PARSE_ERROR`/`TRUNCATED_JSON`이면 **보정 1회** |
| 릴스+쇼츠 | 채널당 위와 같음 → 보통 **2회**, 최악 **4회** (채널별 보정) |
| `MarketingLlmAuthGuard.isCircuitOpen()` | **0회**. 상태 `LLM_AUTH_CIRCUIT_OPEN` |
| `LLM_TRANSIENT_ERROR` (세션 한도·timeout 등) | 해당 채널 **보정 없음** (1회에서 끝) |
| `session limit` / `hit your session` | 인증 오류로 치지 않음. 회로를 열지 않음 |

### 3.2 프롬프트 및 대본 길이 상한 (2026-08-23 조정)

프롬프트는 채널 전용 필드를 유지한다(훅·script 길이·시봄이 장수가 다름). 본문은 `BODY_PROMPT_MAX=900`자로 자른다. 30장 카탈로그 dump 금지.

**대본 길이 상한 축소** (절단 재시도 로직 도입):

TTS 실측 속도: `TTS_CHARS_PER_SEC = 10.19` 글자/초

| 채널 | 목표 시간 | 목표 글자수 | 상한 (2026-08-22 이전 → 이후) | 근거 |
|---|---|---|---|---|
| 릴스 | 13~16초 | 148자 | 220 → **170자** | 중앙값 148자 + 16.7초(170자) 기준 |
| 쇼츠 | 16~20초 | 183자 | 320 → **205자** | 중앙값 183자 + 20초(205자) 기준 |

### 3.3 절단 감지 및 재시도 (2026-08-23)

**배경**: 15일 기준 마케팅 발행 실패율 44.5% — LLM 응답이 문장 중간에서 절단되는 것이 주원인(llm-bridge.md § 응답 절단 감지).

**절단 감지**:
- `VideoVariantService.looksLikeTruncatedJson()` 메서드가 JSON 스키마 누락 징후 감지:
  - 끝이 `[` 또는 `:[` (배열 시작만 함)
  - 끝이 `"` 인데 `"}` / `"]` / `"],` 가 아님 (필드 중단)
- 파싱 실패 시 상태 `TRUNCATED_JSON`으로 분류

**재시도 조건**:
- 첫 호출이 `TRUNCATED_JSON` 상태면 보정 호출 발동
- 보정 지시: `"응답이 중간에 끊겼습니다. 훅과 대본을 더 짧게(한두 문장) 다시 작성하고, sibom_plan은 필수 항목만 간결하게 작성하세요."`
- 대본 길이 상한이 이미 축소됐으므로 재시도 시에도 같은 상한 적용

**제한**: 근본 해결은 아니다 — 출력 토큰 상한을 llm-worker까지 전달해야 하는데 ai-user/llm 요청 DTO에 maxTokens 수용 필드가 없어서다(2회 시도 끝 되돌림). 다음 작업으로 남겨진다.

### 3.4 ASM 연동

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

orchestrator는 모든 LLM 호출을 `[LLMSTATS]` 단일행 로그로 기록한다. 형식은 [`docs/ai-user/orchestrator.md` § admin/metrics](../30-components/orchestrator.md)를 참조.

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
