# Step 42 — R3: Source guard + re-contamination blocking

## 일시
2026-06-17

## 결정
D-43: AS + ML 양면 가드로 human글이 ai로 재오염되는 경로 차단

## 한 일
- AS side (backend)
  - AiUserMlClient.pushNegative: label='ai'일 때 source='SELF_GENERATED' 추가 (new field)
  - AI 유저 자가 생성 콘텐츠만 학습에 기여
- ML side (routes_corpus.py)
  - /corpus/ingest 엔드포인트: label='ai'이면 source='SELF_GENERATED' 필수 검증
  - 위반 → 400 Bad Request (자동 버려짐)
- Docker image 재빌드 (ML 변경 반영)

## 가드 체크포인트
| 경로 | 가드 |
|---|---|
| user_input → inference | PromptSanitizer (기존) |
| AI output → post | ContentSafetyGuard (기존) |
| **SELF_GENERATED → /ingest** | **source='SELF_GENERATED' 필수 (신규)** |

## 효과
- human 글 → AI 재오염 루프 차단
- 학습 데이터 품질 보증

## 검증
- AS + ML 사이 계약 일치 ✓
- Docker rebuild 완료

## 다음
- R4: CLIEN de-counselor 프로필 추가
- R5: MAUVE 측정 (전/후 비교)
