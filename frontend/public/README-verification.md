# 검색엔진 소유확인 파일

이 디렉토리의 `naver*.html` / `google*.html` 파일은 **삭제하지 말 것.**

네이버 서치어드바이저·구글 서치콘솔이 사이트 소유를 확인할 때 주기적으로 다시
읽는다. 지우면 소유확인이 풀리고 색인 요청·사이트맵 제출 권한을 잃는다.

- `naver79b8914327d3fa9c1bacd9df6b05b40b.html` — 네이버 서치어드바이저 (2026-08-29 등록)
  내용은 `naver-site-verification: <파일명>` 한 줄이며 공개 토큰이라 비밀정보가 아니다.

메타태그 방식(`NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION` ·
`NEXT_PUBLIC_NAVER_SITE_VERIFICATION`)과 병행 가능하다 — 그쪽은
`app/layout.tsx`의 `metadata.verification`이 처리한다.
