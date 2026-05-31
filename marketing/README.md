# 마케팅 자동화 (Marketing) — dev 전용

다시봄 마케팅 관련 자원을 한곳에 모은 통합 디렉토리입니다. **모두 dev 환경 전용**이며 prod compose에는 포함되지 않습니다.

## 구성

| 하위 디렉토리 | 내용 | 컨테이너 / 포트 |
|---|---|---|
| [`docs/`](docs/README.md) | 마케팅 전략 문서 (포지셔닝·페르소나·로드맵·바이럴 메커니즘·콘텐츠 캘린더) | — (문서) |
| `renderer/` | 이미지 렌더링 사이드카 (Node.js + Playwright + Sharp) — 카드뉴스·인용·채팅 스크린샷 PNG 생성 | `againspring-marketing-renderer-dev` / 9000 |
| `social-poster/` | 소셜 자동 포스팅 사이드카 (Playwright) — X·Instagram 세션 재사용 발행 | `againspring-social-poster-dev` / 9100 |

## 운영 / 배포

- **dev 전용**: `env/docker-compose.dev.yml`의 `marketing-renderer-dev` / `social-poster-dev` 서비스. prod compose에는 추가하지 않음.
- 빌드 컨텍스트: `renderer/` → `../marketing/renderer`, `social-poster/` → `../marketing/social-poster`
- 핫리로드: `social-poster/src`는 호스트 bind mount + nodemon → `docker compose restart againspring-social-poster-dev`만으로 반영

## 관련 문서 (권위본)

- 마케팅 자동화 전체: [`shared/docs/v15/marketing-automation.md`](../shared/docs/v15/marketing-automation.md)
- dev 전용 정책 / prod 게이트: [`shared/docs/v15/marketing-dev-only-policy.md`](../shared/docs/v15/marketing-dev-only-policy.md)
- 소셜 자동 포스팅 runbook: [`shared/docs/v15/social-auto-posting-runbook.md`](../shared/docs/v15/social-auto-posting-runbook.md)
- 소셜 포스터 장애 대응: [`shared/docs/v15/social-poster-troubleshooting.md`](../shared/docs/v15/social-poster-troubleshooting.md)
