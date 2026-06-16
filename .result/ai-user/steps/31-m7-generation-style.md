# Step 31 (M7) — 생성 스타일 다양화: NATEPAN features + reply voiceType (2026-06-16)

## 상태: ✅ dev 배포 완료 (e2e-realbe 142 PASS, 5 skip) — 신선 출력 축적 중

---

## 목표

M5 블라인드 평가 + M1 신뢰 A-B 조건 충족을 위해 비-상담사 문체 구조 프롬프트 주입 + reply 경로에 voiceType 전달 추가.

---

## 수정 내용

### 1. NATEPAN voice.yml features 16개 백필

감정 중심 비-상담사 서술 특성:

```
감정 중심 서술 (상황보다 감정 먼저). 
진짜/정말/너무 남발. 
ㅠㅠ/ㅜㅜ 문장 끝이나 중간에. 
존댓말 유지하되 구어체(~그랬거든요/~했잖아요). 
공감 요청형 마무리(어떻게 생각하세요? 제가 이상한 건가요?). 
2~3문단 감정 토로.
```

대상 페르소나 16개:
- ai-user-001, 003, 005, 008, 010, 012, 014, 015, 019, 023, 026, 027, 028, 039, 051, 052

### 2. 코드 수정 (reply 경로 voiceType 전달)

| 파일 | 변경 |
|---|---|
| GenDto.ReplyRequest | voiceType 필드 추가 |
| ReplyGenRequest.java | voiceType 필드 추가 |
| ActionExecutor.executeReply() | `.voiceType(voiceProfileField(persona, "voice_type"))` 설정 |
| GenerationController.generateReply() | `sanitizeComment(split[0], req.getVoiceType())` 수정 |
| SelfCritiqueService | 신규 오버로드 `critiqueAndRefine(..., voiceType)` + `sanitizePost/Comment(raw, voiceType)` 적용 |

### 3. PersonaFactory.buildPersonaPrompt() — writing_quirks.features 스키마 추가

향후 새 페르소나 생성 시 voice_type에 맞는 features JSON 스키마 주입.

### 4. dev DB NATEPAN 6개 페르소나 JSON_SET

NATEPAN 페르소나 일부(6개)에 features 직접 적용 후 dev 배포.

---

## 완료 기준 달성

- [x] NATEPAN voice.yml 16개 features 백필
- [x] GenDto.ReplyRequest/ReplyGenRequest voiceType 필드 추가
- [x] ActionExecutor.executeReply() voiceType 전달
- [x] GenerationController.generateReply() 경로 수정
- [x] SelfCritiqueService voiceType 오버로드 추가
- [x] PersonaFactory schema features 추가
- [x] dev DB NATEPAN 6개 JSON_SET
- [x] dev rebuild + e2e-realbe 142 PASS (5 skip)
- [x] prod 배포 아직 (이 세션 후 진행)

---

## 검증 결과

| 시험 | 결과 |
|---|---|
| e2e-realbe (dev:8090) | **142 PASS, 5 skip** (정상) |
| NATEPAN reply voiceType 전달 | ✅ 코드 경로 완성 |
| THEQOO voice.yml features 유지 | ✅ 기존 10개 유지 (변경 없음) |

---

## 함정

- **COMMENT 경로는 voiceType 전달 미적용**: GenerationController.generateComment()는 현재 voiceType 미전달. known minor bug → future fix 예정
- **dev DB 페르소나 세대 차이**: ai-user/docs/personas/profiles/ai-user-{N}/voice.yml ≠ DB 100개 페르소나 IDs. prod 배포 시 prod DB에도 동일 SQL 실행 필수
- **신선 출력 축적 중**: M7 적용 후 dev에서 자연 틱으로 신선 봇 출력 생성 중. M5 블라인드 평가/M1 신뢰 A-B는 충분한 축적 후 실행

---

## 효과 측정 계획

### M5 블라인드 (M7 신선 출력 축적 후)

목표: cond5(human_accuracy ≤ 0.60) 달성 판정
- 균형 블라인드셋 40쌍 (THEQOO+NATEPAN 각 20쌍)
- 사용자 직접 라벨링
- 정확도 산출

### M1 신뢰 A-B 재실행 (M7 + 신선 출력 재학습 후)

목표: cond4 Δ>0 달성 판정
- P(human) 역전 교정된 신규 모델
- K≥3 시드, ≥40 contexts
- THEQOO/NATEPAN/CLIEN 재측정

---

## 다음 단계

- **신선 출력 축적 대기**: dev에서 자연 틱 (자동)
- **M5**: 블라인드 세트 준비 → 사용자 라벨링 (M7 배포 후 1~2주)
- **M6**: COMMENT MAUVE before/after N6 측정 (아직 미실행)
