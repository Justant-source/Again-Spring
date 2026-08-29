/**
 * 서버 사이드(서버 컴포넌트·sitemap·OG 이미지)에서 백엔드를 직접 부를 때 쓰는 base URL.
 *
 * 브라우저 fetch는 상대경로 `/api/...`를 쓰고 nginx가 백엔드로 라우팅한다. 하지만 서버
 * 사이드 렌더는 컨테이너 안에서 실행되므로 상대경로가 통하지 않고, Next의 rewrites도
 * (nginx가 `/api/*`를 먼저 가로채므로) 도달하지 않는다. 컨테이너 네트워크 이름으로
 * 직접 불러야 한다.
 *
 * 🔴 순서가 중요하다. compose가 실제로 주입하는 값은 `BACKEND_INTERNAL_URL`이고
 * (`docker-compose.{dev,prod}.yml`의 frontend 서비스 `environment:` 블록),
 * `API_BASE_URL`은 컨테이너에 설정되지 않는다 — `next.config.mjs`의 rewrites가 로컬
 * 개발용으로만 참조하는 값이다. 이 순서를 뒤집거나 `API_BASE_URL`만 쓰면 컨테이너에서
 * `http://localhost:8080`으로 폴백해 조용히 빈 응답을 받는다(2026-08-29에 sitemap과
 * 홈/광장 SSR이 이 함정에 걸릴 뻔했다).
 */
export const SERVER_API_BASE =
  process.env.BACKEND_INTERNAL_URL ||
  process.env.API_BASE_URL ||
  'http://localhost:8080';
