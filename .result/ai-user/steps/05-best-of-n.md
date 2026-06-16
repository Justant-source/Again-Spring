# Step 5 완료 기록 — AS Best-of-N 와이어링

**날짜**: 2026-06-16  
**세션**: 4  
**상태**: ✅ 완료 (63/63 Java 테스트 통과 + e2e-realbe 142/147 통과 + main push)

---

## 한 일

### 신규 파일 (AS ai-user/orchestrator)

| 파일 | 역할 |
|---|---|
| `client/AiUserMlClient.java` | WSL ML 서비스 REST 클라이언트 (AiLearningClient 패턴 복제 + Bearer 인증) |
| `test/.../client/AiUserMlClientTest.java` | 13개 단위 테스트 (disabled/guard/graceful-fail/JSON 파싱) |

### 수정 파일

| 파일 | 변경 |
|---|---|
| `task/ActionExecutor.java` | Best-of-N 로직 + AI negative push (2곳) |
| `resources/application.yml` | `ai-user-ml:` 블록 5개 프로퍼티 추가 |
| `test/.../task/ActionExecutorStyleHelpersTest.java` | 생성자 null 인수 1개 추가 (AiUserMlClient 추가분) |
| `docs/env/environment-variables.md` | AI_USER_ML_* 변수 5종 문서화 |

---

## 아키텍처 흐름

```
ActionExecutor.executePost()
    ├─ ai-user-ml.enabled=false (기본)
    │   └─ 기존 단일초안 + 반복가드 → 동일 경로
    └─ ai-user-ml.enabled=true
        ├─ N=best-of-n 루프: llmClient.generatePost() × N
        │   → List<CandidateItem> [(draft-0, text), (draft-1, text), ...]
        ├─ AiUserMlClient.rerank(community, "POST", candidates)
        │   ├─ 성공: winner_id → 해당 text 선택
        │   └─ 실패 (WSL 다운/타임아웃): 첫 번째 초안 폴백
        └─ 기존 반복가드 + min-length 가드 + safetyGuard → 게시
            └─ on success: aiUserMlClient.pushNegative(community, "POST", body)

ActionExecutor.executeComment()
    └─ 기존 단일생성 (Best-of-N 미적용 — 댓글은 짧음)
        └─ on success: aiUserMlClient.pushNegative(community, "COMMENT", text)
```

---

## AiUserMlClient 설계 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| `enabled=false` 기본 | ✅ | AI negative 미축적 상태에서 AUC < 0.5 (반사 학습) → 활성화 시 오히려 품질 하락 |
| `timeout=500ms` | ✅ | tick(10분 주기)이 ML 서비스 응답 대기로 멈추는 것 방지 |
| Bearer 토큰 | ✅ | AiLearningClient(내부 네트워크, 무인증)와 달리 Tailscale 외부 서비스 |
| `pushNegative` fire-and-forget | ✅ | 코퍼스 push 실패는 판별기 정확도에만 영향, 게시 동작 차단 불가 |
| `executeReply` 제외 | ✅ | 대댓글은 짧고 맥락 의존 — 판별기 피처가 덜 유효, LLM 호출 N배 비용 대비 이득 적음 |

---

## community 필드 값 소스

`voiceProfileField(persona, "voice_type")` → "NATEPAN", "DCINSIDE", "THEQOO", "CLIEN" 등  
voice_type이 없는 페르소나(voice_profile=null)는 null 전달 → ML 서비스가 null community 처리:  
- `/rerank`: ScoreRequest.community 필수 필드 → 422 예외 → catch → Optional.empty() → 폴백  
- `/corpus/ingest`: CorpusIngestItem.community 필수 → 422 → catch → silent skip

---

## 검증 기록

```
# orchestrator 빌드 검증
./gradlew test → 63/63 PASS (신규 13 AiUserMlClientTest 포함)

# dev 배포 검증
docker compose build ai-user-orchestrator → 성공
docker compose up -d ai-user-orchestrator → 5.159s 기동

# prod 게이트 (e2e-realbe)
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
→ 142 passed, 5 skipped (LLM 호출 차단 픽스처 정상)

# main push
git push origin main → bbacdefc → 6b4d29e9
```

---

## Step 5 후 즉시 조치

```bash
# WSL: AI negative 축적 후 재학습 트리거 (job ID: 01KV5YSEYFGKTKJVDBPAN6RDSY)
curl -sf -X POST http://localhost:8201/train \
  -H "Authorization: Bearer aiuser-ml-api-token-dev-2026" \
  -d '{"contentType":"POST","idempotency_key":"step5-retrain-2026-06-16"}'
```

목표: 커뮤니티별 n_ai ≥ 30 후 AUC 재측정 (목표 ≥ 0.55)

---

## 완료 기준

| 기준 | 결과 |
|---|---|
| `AiUserMlClient.java` 신규 (Bearer, /rerank, /corpus/ingest) | ✅ |
| enabled=false 시 기존 동작과 동일 | ✅ (`isEnabled()` 분기) |
| Best-of-N N=4 초안 생성 + rerank | ✅ |
| WSL 다운 시 graceful fallback | ✅ (catch → Optional.empty() → firstValid) |
| executeComment AI negative push | ✅ |
| executePost AI negative push | ✅ |
| `application.yml` + docs 갱신 | ✅ |
| 63/63 Java 테스트 | ✅ |
| e2e-realbe 142/147 | ✅ |

---

## 함정 기록

- **import 누락**: `ActionExecutor.java`에 `AiUserMlClient` import 추가 필요 — 에이전트가 import 추가를 빠뜨림 → 컴파일 오류 → 직접 수정
- **생성자 아리티**: `@RequiredArgsConstructor` 추가 필드 → `ActionExecutorStyleHelpersTest.bareExecutor()`의 null 리스트 1개 추가 필요
- **voice_type null**: persona가 voice_profile=null이면 community=null → ML 서비스 422 → catch → silent skip (정상 동작)

---

## 다음 구체 작업 (Step 6 — 분포 매칭 개편)

AS `ai-user/llm/` 수정:
- `SelfCritiqueService.java`: 커뮤니티 실측 분포 대조
- `OutputSanitizer.java`: 확률적 분포 매칭 (comma_rate / spacing_error_rate / typo / 초성체)
- `docs/ai-user/personas/voices.yml`: `post_processing` 섹션 신규
