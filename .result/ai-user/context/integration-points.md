# AS 측 수정 지점 — 파일·메서드·라인

> Step 2(export 엔드포인트), Step 5(Best-of-N), Step 6(분포매칭)에서 수정할 AS 파일.
> 탐사 결과 기반 (2026-06-15). 라인 번호는 참조용 — 코드 변경 후 drift 발생 가능.

## Step 2 — Learning 서비스 export 엔드포인트 추가

**파일**: `ai-user/learning/app/api/examples.py`  
**추가 위치**: 파일 끝 (현재 `/count` 엔드포인트 다음)  
**패턴**: 기존 `CamelCompatModel` + `run_query` 패턴 재사용

```python
# 추가할 엔드포인트 (읽기 전용)
@router.get("/export")
def export_examples(
    content_type: Optional[str] = None,  # POST | COMMENT
    source_class: str = "human",          # human | ai | all
    since: Optional[str] = None,          # ISO datetime 커서
    limit: int = 5000,
    offset: int = 0,
) -> dict:
    # source_class='human' → WHERE source != 'SELF_GENERATED'
    # source_class='ai'    → WHERE source  = 'SELF_GENERATED'
    # source_class='all'   → no filter
    ...
```

**주의**: `example_bank`에 `voice_id` 컬럼 없음 → AI negative의 커뮤니티 라벨은
AS ActionExecutor가 push 시 직접 전달 (option b, Step 5).

## Step 5 — ActionExecutor Best-of-N 삽입

**파일**: `ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/task/ActionExecutor.java`

### executePost (~L336-477)
현재 흐름:
```
buildTopicSeed → RAG inject → loadRecentBodies → llmClient.generatePost(genReq) [단일]
→ maxBigramJaccard 반복가드 (1회 재생성) → safetyGuard.check → backendBot.createPost
→ writeHistory → aiLearningClient.saveAsync("SELF_GENERATED")
```

변경: `llmClient.generatePost(genReq)` 단일 호출을 N회로 교체 + `/rerank` 삽입:
```java
// 기존 단일 호출 대체
if (aiUserMlClient.isEnabled() && contentType in applyTo) {
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < bestOfN; i++) {
        String draft = llmClient.generatePost(genReq);
        if (draft != null && !draft.isBlank())
            candidates.add(new Candidate(String.valueOf(i), draft));
    }
    Optional<RerankResponse> reranked = aiUserMlClient.rerank(persona.getVoiceId(), "POST", candidates);
    rawBody = reranked.map(r -> findById(candidates, r.getWinnerId())).orElse(candidates.get(0).getText());
} else {
    rawBody = llmClient.generatePost(genReq); // 기존 경로
}
// 이후 기존 흐름: cleanLlmMetaText → safetyGuard.check → backendBot.createPost
// + AI negative push: aiUserMlClient.pushNegative(rawBody, persona.getVoiceId(), "POST")
```

### executeComment (~L179-263) 동일 패턴
단, `GenResult` (reactions 포함) 구조 유지 필요. winner의 `reactionsJson`이 존재해야 함.

### executeReply (~L265-334) — 제외 (`apply-to`에서 REPLY 미포함)

## Step 5 — 신규 AiUserMlClient.java

**파일 위치**: `ai-user/orchestrator/src/main/java/com/againspring/aiuser/orchestrator/client/AiUserMlClient.java`  
**패턴**: `AiLearningClient.java` 복제 (enabled 플래그, graceful-skip, RestClient, 짧은 타임아웃)  
**추가 차이점**: Bearer 토큰 헤더 (`Authorization: Bearer ${ai-user-ml.api-token}`)

## Step 5 — Orchestrator application.yml 추가

**파일**: `ai-user/orchestrator/src/main/resources/application.yml`  
**추가 위치**: `ai-learning:` 블록 (~L67) 바로 다음

```yaml
ai-user-ml:
  base-url: ${AI_USER_ML_BASE_URL:http://100.115.252.61:8201}
  api-token: ${AI_USER_ML_API_TOKEN:aiuser-ml-api-token-dev-2026}
  enabled: ${AI_USER_ML_ENABLED:false}
  best-of-n: ${AI_USER_ML_BEST_OF_N:4}
  request-timeout-ms: ${AI_USER_ML_TIMEOUT_MS:8000}
  apply-to: ${AI_USER_ML_APPLY_TO:POST,COMMENT}
```

## Step 6 — SelfCritiqueService 개편

**파일**: `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/SelfCritiqueService.java`  
**변경**: 하드 패널티 → 커뮤니티 실측 분포 대조

현재 패널티 목록 (제거/완화 대상):
- L89: PERIOD_AT_EOL → -2 (커뮤니티별 온점 실측치와 대조로 교체)
- L95: DOUBLE_QUOTE → -2 (유지, 전 커뮤니티 공통)
- 종결어미 단조: 커뮤니티 JS발산으로 교체
- 쉼표: 신규 추가 — 커뮤니티 실측 쉼표율과 대조

**베이스라인 데이터 의존**: Step 3 `/eval/baseline` 결과에서 커뮤니티별 `comma_rate`, `ending_dist` 로드.

## Step 6 — OutputSanitizer 분포매칭 확장

**파일**: `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/OutputSanitizer.java`  
**변경**: 결정론적 strip → voices.yml `post_processing` 기반 확률적 주입

**관련 YAML**: `ai-user/docs/personas/voices.yml` — 12개 voice 각각에 `post_processing:` 블록 신규 추가  
**참고 피처**: `comma_rate`, `spacing_error_rate`, `typo_inject`, `초성체_inject`, `sample_prob`
