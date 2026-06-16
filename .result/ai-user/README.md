# Again-Spring-AI-User — 작업 기록 (records)

이 디렉토리는 `Again-Spring-AI-User`(WSL 3090에 신설하는 GPU ML 서비스) 구축의 **다중 세션 순차 개발 기록**이다.
목표: ai-user 페르소나의 글쓰기를 "탐지 가능"에서 "인간 구분 불가"로 — 판별기 인더루프 + 평가 하네스 + 분포매칭(연구문서 Phase 0–1).

> 승인된 마스터 플랜: `/home/justant/.claude/plans/wild-splashing-firefly.md`
> 연구 원본: `.request/ai-user/again-spring-ai-humanness-research-ko-v2.md`

## 운영 규칙 (매 세션 반드시)

1. **세션 시작:** `STATE.md`를 **먼저** 읽는다 → 현재 Step·다음 작업·블로커 파악.
2. 필요한 `context/*.md`만 읽는다 (재탐사 금지 — 이미 정리돼 있음).
3. 작업한다 (auto 모드: 막히지 않으면 계속 진행, 사용자에게 매번 확인하지 않음).
4. **세션 끝:** `steps/NN-*.md`에 *한일·결정·함정·다음 스텝이 알아야 할 것*을 남기고, `STATE.md`를 **마지막에** 갱신한다.

## 파일 지도

| 파일 | 역할 |
|---|---|
| `STATE.md` | 라이브 포인터 — 현재 Step#, 상태, 다음 구체 작업, 미해결 질문, 블로커. 매 세션 갱신. |
| `roadmap.md` | Step 0–7 마스터 체크리스트 (목표/입력/산출/완료기준, 하드 순서). |
| `decisions.md` | 결정 로그 (append-only) — 사용자 4문항 답 + 설계 결정 + 근거. |
| `context/architecture.md` | 목표 아키텍처 — 포트·IP·토큰·데이터 흐름. |
| `context/coupling-map.md` | 현 ai-user의 DB 결합 사실 (orchestrator를 옮기지 않는 이유). |
| `context/wsl-environment.md` | WSL 박스·GPU 사실 + ASM 네트워킹 선례 + VRAM 예산. |
| `context/research-distilled.md` | 연구문서 Phase 0–1 실행 항목 압축. |
| `context/integration-points.md` | AS측 정확한 수정 지점 (파일·메서드·라인). |
| `steps/NN-*.md` | 스텝별 완료 기록 (작업하며 생성). |

## 핵심 좌표 (빠른 참조)

- AS(커뮤니티 본체): Ubuntu, Tailscale `100.81.189.92`, GPU 없음. 리포 `/home/justant/Data/Again-Spring`.
- WSL(신규 ML): Tailscale `100.115.252.61`, RTX 3090 24GB. 신규 리포 `~/Data/Again-Spring-AI-User` (예정).
- 신규 서비스 포트: **8201** (ASM=8200, learning=8099, AS 콜백=8090과 구분).
