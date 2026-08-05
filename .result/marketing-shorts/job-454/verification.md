# Marketing Short #454 검증

- AS job: `454` (`READY`)
- ASM job: `01KZ7ZF27JC5R6HS7KZY1E2TTX`
- post: `post_da50d3b1384c4c8c979f`
- publish: `autoPublish=false` — 생성만 완료, 미게시
- video: `marketing-short-454.mp4`
- SHA-256: `e022ca2569dff46fca1535ae3d35f391dfa68514dc3ac32c23ed8ac0b125fcd7`

## 실측

- 1080×1920, H.264, CFR 30fps, 1673 frames
- video `55.766667s`, audio `55.770000s`, stream delta `3.333ms`
- 첫 text→speech `0.149592s`
- outro text→speech `0.150159s`
- integrated loudness `-16.7 LUFS`, true peak `-1.5 dBFS`
- faster-whisper alignment: 26줄, confidence `1.000`
- 화면 확인: 첫 텍스트 선표시, 본문 1→2→3줄 누적 후 reset, 새 solo closing 유지

## 참고

- SceneDirector의 선택적 LLM 요청은 504가 발생했으나 rule-based fallback으로 렌더가 정상 완료됐다.
- thumbnail artifact는 기존 ASM 정책의 1×1 placeholder이며 이번 비디자인 작업에서는 변경하지 않았다.
