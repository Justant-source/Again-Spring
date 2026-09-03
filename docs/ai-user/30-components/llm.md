# AI User LLM Service

`ai-user/llm`은 Spring Boot 워커다. orchestrator가 여기에 글/댓글/대댓글/페르소나 생성과 글 분석을 위임한다.

## 현재 엔드포인트

| 메서드 | 경로 | 역할 |
|---|---|---|
| `POST` | `/generate/post` | 게시글 생성 |
| `POST` | `/generate/comment` | 댓글 생성 |
| `POST` | `/generate/reply` | 대댓글 생성 |
| `POST` | `/generate/persona` | 페르소나 JSON 생성 |
| `POST` | `/generate/proofread` | 게시 직전 맞춤법 교정 — persona/voice 없음, 의미·구조 보존하고 오탈자만 수정. orchestrator는 오탈자 휴리스틱(`됬` 등)이 맞을 때만 호출하고, 호출/구조/안전 실패 시 **원문 유지**(fail-open, 2026-08-18) |
| `POST` | `/internal/rewrite/post` | legacy synthetic 게시글 부분 교정용 내부 rewrite |
| `POST` | `/analyze/post` | 좋아요/투표용 구조화 post 분석 |
| `POST` | `/v2/generate/thread-plan` | PLAN 모드의 AI 글 묶음 또는 사람 글 후보 plan 구조화 생성. AI_POST `post`에 optional `capture_split_after_lines`(개행 블록 N장 컷) + required-for-AI `metaphor_ids`(3~5개, 적합도 순, 60종 메타포 일러스트 — `metaphor_id`는 `metaphor_ids[0]` 하위호환 값). 요청에 optional `overused_metaphor_ids`(최근 다빈도 사용 상위 10개 — orchestrator가 `post_metaphors` 집계로 산출, 프롬프트에 회피 힌트로 주입) |
| `POST` | `/v2/generate/human-replies` | 30분 사람 interaction batch — comment당 0~3 reply(`candidateResponders`에서 선택) |
| `POST` | `/v2/generate/paired-phase1` | 양면 사연 **logical Call1** (`PAIRED_PHASE1`): 작성자(A) post + phase1 댓글(작성자만 그라운딩, ~2–4 최상위) |
| `POST` | `/v2/generate/paired-phase2` | 양면 사연 **logical Call2** (`PAIRED_PHASE2`): 상대방(B) body + phase2 댓글(작성자+상대+공개 최상위 댓글 최대 5–8) |
| `GET` | `/v1/metrics` | 워커 풀 상태 |
| `POST` | `/internal/prompts/reload` | prompt template 재로드 |

`8092` 포트는 compose 내부 네트워크 전용이다. dev/prod 모두 host port publish가 없다.

## PLAN 모드 bridge

`/v2/generate/*`는 legacy API backend selector를 받지 않는다. provider는 request의 workload/provider snapshot으로 명시하며 `CLAUDE` 또는 `CODEX`만 허용한다. `OFF` workload는 orchestrator가 job을 만들지 않으므로 bridge에 요청되지 않는다.

- Claude와 Codex는 같은 worker image의 CLI로만 실행한다. API key/direct API 경로나 provider fallback은 PLAN 모드에서 사용하지 않는다.
- Codex는 새 세션으로 실행(`codex exec --ephemeral`)해 이전 게시글의 대화 context를 재사용하지 않는다.
- provider별 논리적 queue/concurrency/timeout을 적용한다. 선택되지 않은 CLI process는 상주하지 않는다.
- 입력/출력은 구조화 contract로 검증하며 요청 전체가 유효하지 않으면 `INVALID_STRUCTURED_REQUEST`, queue 포화는 `CAPACITY`, timeout은 `TIMEOUT`으로 응답한다. bridge 오류 문자열은 절대 게시 콘텐츠가 될 수 없다.
- `ThreadPlanRequest.minTopLevel` / `minItems`: null이면 `parsePlan`이 레거시 하한(최상위 min(6,maxTopLevel) · 전체 min(12,max))을 적용한다. 명시 값(1 포함)은 `1..max`로 clamp해 존중한다. 품질 드롭 가능 plan을 원하는 orchestrator는 `minTopLevel=1`, `minItems=1`을 보낸다.
- **STORY_PERSONA_RULE**: `AUTHOR.personaId`(및 양면 Call2의 작성자·상대방)는 댓글/대댓글 persona로 쓸 수 없다. 파서가 해당 item을 드롭하고, orchestrator `ThreadQualityGate`/`StoryPersonaCommentFilter`/`ThreadPlanPublisher`가 재차단한다.
- 실제 model identifier는 환경변수로 주입한다. Codex Terra/Luna alias는 운영 호스트에서 검증된 identifier에만 매핑한다.

### 양면 사연 Call1 / Call2 (`PAIRED_PHASE1` · `PAIRED_PHASE2`)

solo `thread-plan` mega/micro-batch와 분리된 **paired 전용** 구조화 워크로드다. 스키마: `schemas/paired-phase1.schema.json`, `schemas/paired-phase2.schema.json`. 프롬프트 가이드: `voice/paired_phase1.md`, `voice/paired_phase2.md` (+ 작성자 `post_paired_author.md` / 상대 `partner.md`).

| Workload | 경로 | 요청 핵심 | 응답 핵심 |
|---|---|---|---|
| `PAIRED_PHASE1` | `POST /v2/generate/paired-phase1` | `author`, `personas`, `category`/`topicHint`, optional reconstruct/source, `maxTopLevel`≈4, optional `overused_metaphor_ids` | `workload`, `post`{title,body,promo_title,hook_emotion,capture_split_after_lines,metaphor_id,metaphor_ids}, `items`[] |
| `PAIRED_PHASE2` | `POST /v2/generate/paired-phase2` | `authorPost`{title,body}, `partner`, `personas`, `publishedTopLevelComments`[0..8], `includePartnerPost` | `workload`, `partner_post`{body,capture_split_after_lines}\|null, `items`[] |

- **phase1**: 상대방 본문 없음. 댓글은 작성자만 본 것처럼. 기본 min top-level=2.
- **phase2**: 공개 최상위 댓글 0개면 본문만으로 댓글 생성. `includePartnerPost=false`면 `partner_post=null` (logical Call2의 댓글-only 마이크로배치 후속).
- 파싱 가드: AI_POST와 동일(제목 4~40·제목≠본문·promo/capture sanitize·`LlmErrorSignature`/META). 모델은 post-grade(Claude Sonnet / Codex Terra).
- **마이크로배치**: 논리 단계는 항상 Call1/Call2 두 번. cast가 크면 Call2만 `includePartnerPost=false` 후속으로 쪼갠다(또는 orchestrator가 기존 `thread-plan` HUMAN_POST 후속을 써도 됨).

세부 후보 규칙과 retry·안전 정책은 [thread-planning.md](../60-runtime/thread-planning.md)를 따른다.

### 검증된 세션 smoke 결과

2026-07-30에 컨테이너에서 provider별로 승인된 단일 구조화 요청을 실행했다. Codex `gpt-5.6-terra`와 Claude `claude-sonnet-5` 모두 schema 유효 JSON을 반환했다. 이 검증은 DB/게시 API에 쓰지 않았으며, 실제 운영 콘텐츠 생성은 별도 승인 범위다.

## Claude CLI tool 오버헤드 감소 (2026-08-21)

Claude Code CLI는 기본적으로 모든 tool 정의를 프롬프트에 함께 전송한다 (~22-25k 입력 토큰). 
다시봄은 structured 생성에서만 `StructuredOutput` 도구가 필요하므로, `--disallowedTools` 플래그로 나머지를 차단한다.

| 모드 | 조건 | --disallowedTools 값 | 오버헤드 | 설명 |
|---|---|---|---|---|
| 구조화 + 스키마 플래그 | `--json-schema` 사용 | `BASH,READ,WRITE,...` (StructuredOutput 제외) | 약 18.8k | 명시 리스트: 스키마 검증 유지 |
| 구조화 + 프롬프트 모드 | `LLM_STRUCTURED_PROMPT_MODE=true` | `*` (모두 차단) | 약 279 token | 모든 도구 차단, 최대 절감. 스키마는 prompt 텍스트로 주입 |
| 비구조화 생성 | schema 없음 | `*` (모두 차단) | 약 279 token | 도구 불필요 |

**왜 StructuredOutput이 필요한가**: `--json-schema` 플래그를 사용할 때, CLI는 모델의 JSON 결과를 stream-json으로 검증하며 전송하고, 실패 시 `STRUCTURED_OUTPUT_ERROR` 에러를 반환한다. 따라서 StructuredOutput 도구를 활성화해야 이 검증이 동작한다. 리스트에서 빼면 스키마 검증이 작동하지 않는다.

**프롬프트 모드 선택 시**: `--disallowedTools "*"`로 전환하되, 스키마를 JSON 텍스트로 system prompt 끝에 주입하고 `JsonExtractorUtil`로 lenient 파싱(direct parse → strip ```json fences → substring 추출)한다. 검증이 없으므로 malformed JSON이 들어오면 손실.

## 실행 모델

### worker pool

`application.yml` 기본값:

| 항목 | 값 |
|---|---:|
| pool size | `20` |
| queue capacity | `100` |
| queue wait timeout | `30000ms` |
| default timeout | `120000ms` |
| base model | `claude-haiku-4-5-20251001` |
| post model override | 빈 값, compose에서는 `claude-sonnet-5` |

**타임아웃 시 프로세스 트리 종료** (2026-09-03): 실행 타임아웃이 지나면 `LlmWorkerPool`이 더 이상 `LlmTimeoutException`만 던지고 CLI 프로세스를 방치하지 않는다. 각 실행은 `ExecutionSlot.open(correlationId)`으로 슬롯을 열고, `ClaudeCliInvoker`/`CodexCliInvoker`가 `ExecutionSlot.attachCurrent(process)`로 실제 프로세스를 슬롯에 연결한다. 타임아웃 스케줄 태스크는 `slot.terminate(processTerminator, "execution-timeout")`을 호출해 `ProcessTerminator`가 프로세스 트리를 죽인 뒤에 예외를 완료시킨다 — 이전에는 타임아웃이 나도 프로세스가 살아남아 워커 슬롯을 영구히 붙잡는 문제가 있었다. `WorkerMetrics.timedOut`(`GET /v1/metrics`)이 누적 타임아웃 건수를 노출한다.

### provider 선택

레거시 API 경로(`backend=CLI|API`)와 신규 `provider` 필드가 공존한다. **PLAN `/v2/generate/*`는 legacy `backend` selector를 받지 않으며 request의 workload/provider snapshot(`CLAUDE`|`CODEX`)만 허용한다.**

각 요청 DTO(`PostGenRequest`·`CommentGenRequest`·`ReplyGenRequest`·`ProofreadRequest`·`PostRewriteRequest`)는 `resolveProvider()`로 최종 provider를 하나로 정한다 — `LlmProvider.parseLegacy(provider, backend)`: `provider` 필드가 있으면 그것을 쓰고, 없으면 구 `backend` 필드(`CLI`→`CLAUDE`, `API`→`API`)로 해석하며, 둘 다 없으면 `CLAUDE`가 기본값이다.

| provider | 라우팅 대상 | 비고 |
|---|---|---|
| `CLAUDE` | `ClaudeCliInvoker` | 기본값. CLI 서브프로세스 env에서 `ANTHROPIC_API_KEY`를 제거해 OAuth 세션 강제 |
| `CODEX` | `CodexCliInvoker` | Codex CLI 세션 |
| `API` | `ClaudeApiInvoker` | `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL`(DB `system_setting` 우선)로 clcocloud 프록시 직접 호출 |
| `STUB` | `StubInvoker` | LLM 미호출. classpath `stub/*` 픽스처 반환(`personaId`는 `__PERSONA_n__` 자리표시자로 치환), `LLM_STUB_FIXTURE_DIR` 설정 시 그 디렉토리를 classpath보다 우선 — dev canary 전용 |

- `InvokerRouter.routeProvider(LlmProvider)`가 위 4종을 라우팅한다.
- prompt caching flag는 `llm.api.prompt-caching`에 있고, compose/env로 제어할 수 있다.
- `/internal/rewrite/post`는 legacy 사연 큐레이션 배치용이며, backend 기본값을 `API`로 잡아 clcocloud 직접 경로를 우선 사용한다.

## provider 인증 상태 (2026-09-03)

CLI exit code≠0일 때 `ClaudeCliInvoker`/`CodexCliInvoker`는 stderr 마지막 2KB를 `CliAuthFailureDetector.isAuthFailure()`로 분류한다 — 세션 만료(`not logged in`, `token has expired`, `invalid_grant` 등)·조직 차단(`organization has disabled`, `subscription access`)·키 무효(`invalid api key`, `401`) 패턴에 걸리면 `errorType=AUTH_ERROR`, 아니면 기존 `CLAUDE_ERROR`/`CODEX_ERROR`로 남는다. 토큰을 태우는 canary 호출 없이 실제 요청 결과로만 판단한다.

`ProviderHealthRegistry`가 provider별(현재 `claude`/`codex`) 상태를 메모리에 들고, `AUTH_ERROR`가 나면 `markAuthDown(provider, reason)`으로 `AUTH_DOWN`을 기록한다. `GET /v1/providers/status`가 이 스냅샷을 반환한다:

```json
{ "claude": { "state": "AUTH_DOWN", "reason": "...", "since": "...", "ttlMinutes": 10 }, "codex": { "state": "UP" } }
```

`AUTH_DOWN`은 `llm.auth-down-ttl-minutes`(env `LLM_AUTH_DOWN_TTL_MINUTES`, 기본 `10`)가 지나면 다음 조회 때 자동으로 `UP`으로 되돌아간다 — 별도 복구 호출이 없어도 TTL 경과 후 재확인 시 정상 취급된다.

orchestrator의 `LlmAvailabilityGate`(5분 cron)가 이 엔드포인트를 폴링해 `llm_generation_gate`를 자동 hold/resume한다 — 상세는 [10-context.md](../10-context.md)의 "글이 하나도 안 올라올 때" 흐름과 [operations.md](../60-runtime/operations.md) §9 참조.

## prompt 조립 모드

`PromptAssembler.assemblePostPrompt()`는 글 종류에 따라 모드가 갈린다.

| 모드 | 조건 | 사용되는 경로 |
|---|---|---|
| 일반 갈등 글 | stance 미지정 | `postGuide` + category guide |
| 양면 사연 작성자(A) | `stance=AUTHOR` | `assembleAuthorPairedPrompt()` + `voice/post_paired_author.md` |
| 양면 사연 상대방(B) | `stance=PARTNER` and counterpart body 존재 | `assemblePartnerPrompt()` + `voice/partner.md` |
| 재구성 | `reconstructMode=true` and `sourceBody != null` | `assembleReconstructPrompt()` |
| 일상 글 | `postKind=CASUAL` | `assembleCasualPostPrompt()` |

추가로 현재 코드에는 다음이 들어 있다.

- `VARIETY_SEEDS`: 문장 종결, 사건 디테일, 감정 마무리 다양화
- `CATEGORY_GUIDE`: 6광장과 내용 불일치 방지
- recent output block: 직전 글/댓글 반복 억제
- **제목/본문 분리**: 제목 공백 포함 12~40자(하드 상한 40), 제목≠본문 — `voice/post.md` · PLAN `planPrompt` · `parsePlan`/`AiPostBundleService` 이중 가드

## 생성 파이프라인

1. controller가 prompt를 조립한다.
2. `LlmWorkerPool`이 sync task를 실행한다.
3. 글은 `OutputSanitizer.sanitizePost()`, 댓글/대댓글은 `sanitizeComment()`를 거친다.
4. 글과 댓글은 `SelfCritiqueService`를 통해 재생성 루프를 탈 수 있다. FAIL 재시도는 **이슈 + 원문 전체 + 반말/존댓말 한 줄**만 보낸다. 원본 thread-plan 프롬프트·소스 본문·페르소나 목록·JSON 스키마는 재첨부하지 않는다. 호출 횟수 전체 표: [llm-call-budget.md](../70-policy/llm-call-budget.md).
5. 댓글/대댓글은 `<<<REACT>>>` sentinel 뒤 JSON을 분리해 orchestrator로 돌려준다.

### PLAN `/v2/generate/*` 개행 정규화

legacy `/generate/*`와 달리 PLAN structured 경로는 전체 `OutputSanitizer`를 타지 않는다.
대신 파싱 직후 `OutputSanitizer.normalizeLiteralNewlines()`로 본문·댓글의 리터럴 `"\n"`/`"\r\n"`만 실개행으로 바꾼다
(LLM이 JSON string 값 안에 백슬래시+n 문자를 넣는 사례 — 2026-07-31~08-01 게시 글에서 확인).
orchestrator 쪽 발행 경계(`AiPostBundleService`·`ThreadPlanPublisher`·`ScheduledPostPublisher` 등)에도
동일 규칙의 `LiteralNewlineNormalizer`를 두어 이중 방어한다.

## legacy rewrite API

- 입력: 기존 `title/body`, 현재 광장, 최종 광장, persona voice block, rewrite instruction
- 출력: JSON `{title, body}`를 파싱한 구조 응답
- 규칙:
  - 새 글 재창작이 아니라 부분 교정만 허용
  - 사건/감정 방향은 유지
  - 결과 body는 `OutputSanitizer.sanitizePost()`를 다시 거친다
  - title은 최대 40자(공백 포함)로 자르고, title/body가 비정상적으로 짧으면 실패로 돌려 배치가 건너뛸 수 있게 한다

## self critique

기본 `application.yml`:

| 설정 | 기본값 |
|---|---|
| `SELF_CRITIQUE_ENABLED` | `false` |
| `SELF_CRITIQUE_THRESHOLD` | `5` |
| `SELF_CRITIQUE_RARE_VOCAB_ENABLED` | `false` |
| `SELF_CRITIQUE_RARE_VOCAB_RATIO` | `0.18` |

compose는 dev/prod 모두 `SELF_CRITIQUE_ENABLED=true`를 넘긴다. rare vocab detector는 기본으로 꺼져 있다.

`quickCheck`는 온점·상투구·강조어·ㅠ·쉼표율·casual **반말 위반(`~요`)** 등을 결정론으로 본다. PASS면 CLI를 더 부르지 않는다. FAIL이면 `buildRetryPrompt`로 짧은 본문 rewrite만 돌린다(timeout 90s, 실패 시 초안 유지). `originalPrompt` 인자는 API 호환용이며 **무시**한다.

운영 로그 FAIL 다수는 casual 페르소나 + 존댓말이지, 원문 1000자·특수문자가 아니다. 호출 횟수·솔로 글 파이프라인은 [llm-call-budget.md](../70-policy/llm-call-budget.md).

## prompt source

**무상태 원칙(2026-09)**: `llm-ai-user`는 DB 커넥션이 전혀 없다(DataSource·`ai_prompt_template` 조회 삭제). guide는 두 군데에서 읽는다.

1. 요청 `promptOverrides`(orchestrator가 `PromptTemplateCache`로 admin이 편집한 `ai_prompt_template`을 읽어 실어 보냄 — [orchestrator.md](orchestrator.md) 참고)
2. classpath `voice/*.md` — override 미존재/공백 시 폴백

우선순위는 `promptOverrides` > classpath. `/internal/prompts/reload`는 classpath만 다시 읽는다(DB는 보지 않음).

## 분석 API

`/analyze/post`는 생성 프롬프트와 분리된 최소 프롬프트를 쓴다.

- 용도: 좋아요/투표를 더 내용 인식적으로 고르기 위한 구조화 신호 추출
- timeout: `30s`
- 응답: JSON 문자열 원문을 orchestrator가 파싱/캐시

## 운영 메모

- compose는 `~/.claude`를 컨테이너에 mount한다. CLI 인증은 호스트 `claude auth login`에 의존한다.
- 글만 Sonnet 승격을 쓴다. 댓글/대댓글은 기본 모델을 따른다.
- 이 서비스 단독으로 게시를 하지 않는다. backend 제출은 항상 orchestrator가 맡는다.
