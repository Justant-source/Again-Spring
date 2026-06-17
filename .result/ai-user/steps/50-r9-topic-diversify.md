# Step 50 — R9 Track B: 주제 다양화 (CASUAL 25% 분기)

**세션**: 22 | **날짜**: 2026-06-17 | **상태**: ✅ 구현·테스트·dev 배포 완료

---

## 배경

모든 AI POST가 갈등 서사 → 주제만으로 식별 가능 (R5 블라인드 100%의 1순위 원인).
인간 CLIEN 글은 정치·음식·주식·스포츠·잡담 다양 → AI만 갈등 = 패턴 탐지 용이.

gal등 프레임은 `PromptAssembler` + `voice/post.md`에 하드코딩 (`구체적 사건 필수`).
이를 **새 CASUAL 모드**로 25% 우회 — 사건 의무 없는 일상/관찰/취향/잡담 글 허용.

---

## 구현 위치

| 파일 | 변경 내용 |
|---|---|
| `ai-user/orchestrator/.../task/ActionExecutor.java` | `executePost` CASUAL 25% 분기 + `buildCasualSeed()` + `CASUAL_FRAMES` |
| `ai-user/llm/.../service/PromptAssembler.java` | `assembleCasualPostPrompt()` 신설 + `casualPostGuide` 로드 |
| `ai-user/llm/.../resources/voice/post_casual.md` | 일상 글 가이드 (갈등 서사 금지, 사건 의무 없음) |
| `ai-user/llm/.../dto/PostGenRequest.java` | `postKind` 필드 추가 |
| `ai-user/orchestrator/.../client/dto/GenDto.java` | `PostRequest.postKind` 추가 (Jackson 브리지) |

---

## 핵심 구현

### executePost CASUAL 분기 (ActionExecutor)

```java
boolean casual = RNG.nextDouble() < 0.25;
String topicSeed = casual ? buildCasualSeed(persona) : buildTopicSeed(persona);
List<> examples = casual ? Collections.emptyList() : aiLearningClient.findSimilar(...);
if (dynamicExamples.isBlank() && !casual) { /* styleFallback */ }
recentBodies = casual ? Collections.emptyList() : loadRecentBodies(...);
builder.postKind(casual ? "CASUAL" : "CONFLICT")
```

- CASUAL: `buildTopicSeed`(갈등 전용) 우회 → `buildCasualSeed`(CASUAL_FRAMES 10개 랜덤)
- CASUAL: dynamicExamples="" (갈등 few-shot 앵커 제거)
- CASUAL: recentBodies 비움 (갈등 히스토리 앵커 제거)

### CASUAL_FRAMES (10개)
`오늘 먹은 것`, `최근에 본 것`, `날씨/계절`, `소소한 불편`, `요즘 관심사`,
`직장 일상`, `가족 일상`, `소비/구매`, `추억/회상`, `관찰/생각`

### assembleCasualPostPrompt (PromptAssembler)
- system: `casualPostGuide` (fallback: inline) + persona section
- user prompt: `"갈등 서사 금지"` + `"사건(trigger) 의무 없음"` + `"큰 결론·해결책 없이 끝내도 됨"` + topicSeed

### voice/post_casual.md
- `[이 가이드는 일상 모드 글 전용 — 구체적 사건 필수 규칙 이 글에 적용 안 함]`
- 허용 주제 유형, 구조 규칙, 온점·쌍따옴표 금지 공통 규칙 유지

---

## 완료 기준 ✅

- [x] `PromptAssemblerStyleTest` + `OutputSanitizerTypoTest` CASUAL/CONFLICT 분기 검증
- [x] `./gradlew :ai-user:llm:test` BUILD SUCCESSFUL
- [x] `./gradlew :ai-user:orchestrator:test` BUILD SUCCESSFUL
- [x] e2e dev:8090 147 passed, 5 skipped (enum 무변경 → spec 수정 불요)
- [x] dev 배포: `againspring-ai-user-orchestrator` healthy

---

## 핵심 결정 (D-51)

- `PostCategory.OTHER` 재사용 — CASUAL은 모드이지 분류 값이 아님
  → BE/DB/FE/topic_synthesizer/e2e 리플 0, 승인 불요
- `voice/post.md` 인플레이스 수정 대신 신규 `voice/post_casual.md`
  → 갈등 75% 경로 무손상

---

## 다음 단계

- 자연 틱으로 신선 CASUAL ai POST 확인 (현실에서 비갈등 글 출현 여부 스팟체크)
- **blind ②**: 혼합주제 20쌍 (인간 다양주제 vs AI CASUAL 포함) → 현실 cond5
- 에스컬레이션 평가: blind①②>60% 이면 D-12 Phase 2/3 진입 조건 사용자 보고
