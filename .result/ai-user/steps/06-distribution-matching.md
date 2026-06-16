# Step 6 완료 기록 — 커뮤니티 분포 매칭 개편

**날짜**: 2026-06-16  
**세션**: 5  
**상태**: ✅ 완료 (28 LLM + 63 Orchestrator Java 테스트 + e2e-realbe 142/147 + main push fd5d16c4)

---

## 한 일

### 수정 파일

| 파일 | 변경 |
|---|---|
| `ai-user/docs/personas/voices.yml` | 12개 커뮤니티 `post_processing` 섹션 신규 (target_comma_rate·chosung_inject·chosung_phrases·sample_prob) |
| `ai-user/llm/.../service/SelfCritiqueService.java` | 쉼표 과다 체크 #11 신규 (>5% → score -1) |
| `ai-user/llm/.../service/OutputSanitizer.java` | VoiceDistribution record + VOICE_DIST 12종 + sanitizePost/Comment(raw, voiceType) 오버로드 + normalizeCommaRate + injectChosung |
| `ai-user/llm/.../controller/GenerationController.java` | sanitizePost/Comment 호출에 `req.getVoiceType()` 전달 |
| `ai-user/llm/.../dto/PostGenRequest.java` | `voiceType` 필드 신규 |
| `ai-user/llm/.../dto/CommentGenRequest.java` | `voiceType` 필드 신규 |
| `ai-user/orchestrator/.../client/dto/GenDto.java` | PostRequest·CommentRequest에 `voiceType` 필드 신규 |
| `ai-user/orchestrator/.../task/ActionExecutor.java` | executePost·executeComment 빌더에 `.voiceType(voiceProfileField(persona, "voice_type"))` 추가 |
| `ai-user/llm/.../service/SelfCritiqueServiceTest.java` | `detectsExcessiveCommaRate` 테스트 +1 |
| `ai-user/llm/.../service/OutputSanitizerHrTest.java` | 분포 매칭 테스트 +3 |

---

## 아키텍처 흐름 (Step 6 이후)

```
ActionExecutor.executePost/Comment()
    └─ voiceProfileField(persona, "voice_type") → "NATEPAN"/"DCINSIDE"/...
        └─ GenDto.PostRequest.voiceType 설정
            └─ (JSON 직렬화 → HTTP POST → llm-ai-user:8092)
                └─ PostGenRequest.voiceType 역직렬화
                    └─ GenerationController.generatePost()
                        └─ outputSanitizer.sanitizePost(raw, req.getVoiceType())
                            ├─ sanitize(raw, MAX_POST) — 기존 결정론적 처리
                            └─ applyDist(base, "NATEPAN", true)
                                ├─ DIST_RNG > sampleProb → early return (30% 확률)
                                ├─ normalizeCommaRate: 쉼표율 1.5배 초과 시 확률 제거
                                └─ injectChosung: 고슬랭 커뮤니티만 초성체 1개 주입
```

---

## 커뮤니티별 분포 설정

| 커뮤니티 | target_comma_rate | chosung_inject | chosung_phrases | sample_prob |
|---|---|---|---|---|
| NATEPAN | 0.011 (실측) | false | [] | 0.70 |
| DCINSIDE | 0.030 (실측) | true | ㄹㅇ·ㅇㅈ·ㄷㄷ·ㅋㅋ | 0.80 |
| BLIND | 0.015 | false | [] | 0.60 |
| GENERAL | 0.015 | false | [] | 0.50 |
| FMKOREA | 0.015 | true | ㄹㅇㅋㅋ·ㄷㄷ·ㅇㅈ·후추 | 0.80 |
| RULIWEB | 0.018 | false | [] | 0.60 |
| THEQOO | 0.011 (실측) | true | 헐·ㅠㅠ·ㄷㄷ·개공감 | 0.75 |
| ARCALIVE | 0.015 | true | ㄹㅇ·ㄱㄱ·ㅇㅇ·어쩔 | 0.80 |
| INVEN | 0.015 | false | [] | 0.60 |
| MLBPARK | 0.020 | false | [] | 0.50 |
| PPOMPPU | 0.015 | false | [] | 0.55 |
| CLIEN | 0.022 (실측) | false | [] | 0.60 |

---

## SelfCritiqueService 쉼표 체크

```
쉼표 수 / 텍스트 길이 > 0.05 (5%)
→ score -1, issues.add("쉼표 과다(AI 투) — 쉼표를 2/3 이상 제거하고 다시 쓸 것")
```

인간 베이스라인 최대: DCINSIDE 3.0% (= 실측 최대). AI 생성 실측: 5-10%.

---

## voiceType 전달 경로

```
persona.voiceProfile["voice_type"]  (예: "NATEPAN")
    → ActionExecutor  voiceProfileField(persona, "voice_type")
    → GenDto.PostRequest.voiceType       (orchestrator → HTTP JSON)
    → PostGenRequest.voiceType           (LLM service DTO)
    → GenerationController req.getVoiceType()
    → OutputSanitizer.sanitizePost(raw, voiceType)
```

**ReplyRequest 제외** (대댓글은 짧아 분포 매칭 효과 적음).

---

## 설계 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| static Map vs YAML 로딩 | static Map (하드코딩) | voices.yml은 문서용, 런타임 YAML 파싱 의존성 불필요 |
| sampleProb | 0.50~0.80 | 매번 적용 시 출력이 너무 기계적, 확률 기반으로 자연스러움 유지 |
| injectChosung 댓글 제외 | false 고정 | 댓글은 MAX_COMMENT=300자로 짧아 초성체 주입이 부자연스러움 |
| normalizeCommaRate 임계: targetRate * 1.5 | ✅ | 목표 50% 초과 시에만 개입, 정상 범위 오제거 방지 |
| backward-compat | 기존 단인자 메서드 유지 | 다른 호출부(테스트 등) 영향 없음 |

---

## 검증 기록

```
# 빌드
cd ai-user/llm && ./gradlew clean test → BUILD SUCCESSFUL (28 tests)
cd ai-user/orchestrator && ./gradlew clean test → BUILD SUCCESSFUL (63 tests)

# dev 배포
docker compose build llm-ai-user ai-user-orchestrator → 성공
docker compose up -d llm-ai-user ai-user-orchestrator → 5.1s 기동

# prod 게이트 (e2e-realbe)
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
→ 142 passed, 5 skipped (LLM 호출 차단 픽스처 정상)

# main push
git push origin main → 6b4d29e9 → fd5d16c4
```

---

## 다음 구체 작업 (Step 7 — 주기 갱신 + 모니터링)

- WSL `Again-Spring-AI-User`: 스케줄 코퍼스 재pull + `/train refresh` 주기 잡
- 주기 `/eval` → AUC/MAUVE 드리프트 추적
- AI_USER_ML_ENABLED 활성화 시점: 커뮤니티별 n_ai ≥ 30 + AUC ≥ 0.55 확인 후
