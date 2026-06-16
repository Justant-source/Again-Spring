# Step 36 (M6-2) — 댓글 길이 제약 구현 (세션 16, 2026-06-16)

## 변경 사항

| 파일 | 변경 내용 |
|---|---|
| voice/comment.md | "🚨 길이" 섹션 추가: "인간 댓글 = 2~5어절, 최대 30~50자" |
| ActionExecutor.java commentModeHint() | 모든 7개 모드 상한 절반 축소 |
| PromptAssembler.java | fallback 길이 "50~150자" → "10~35자 (한 줄, 최대 두 마디)" |
| PromptAssembler.java | 사용자 프롬프트에 "🚨 반드시 초단문" 알림 추가 |

## 변경 전/후 char limit (대표 모드)

| 모드 | 변경 전 | 변경 후 |
|---|---|---|
| REACTION_ONLY | 10~30자 | max 20자 |
| SHORT_AGREE | 10~25자 | max 15자 |
| EXPERIENCE | 40~120자 | max 50자 |
| ADVICE | 20~60자 | max 30자 |

## 배포
- dev 재빌드 후 배포 (ai-user-orchestrator + ai-user-llm)
- e2e-realbe 통과 확인 후 main push 예정

## 완료 기준 달성 여부
- [x] 길이 제약 코드 추가
- [ ] dev 배포 확인
- [ ] COMMENT MAUVE 재측정 (목표: 0.06 → 개선)
- [ ] e2e-realbe 통과

## 다음
- 배포 후 오케스트레이터 댓글 생성 → ML corpus에 신선 COMMENT 축적
- COMMENT MAUVE 재측정 (M6-3)
