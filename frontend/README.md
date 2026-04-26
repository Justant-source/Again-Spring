# 다시봄 · Again Spring — 프론트엔드

> **"다시 봄. 다시 바라봄."**
> 
> 싸운 두 사람 사이에서 AI 중재자가 양쪽 이야기를 중립적으로 정리해, 관계 회복을 돕는 웹앱의 프론트엔드 프로토타입.

---

## 빠른 시작

```bash
npm install
npm run dev          # localhost:3000
```

- MSW (Mock Service Worker)가 자동으로 모든 API 요청을 가로챕니다 (dev 환경).
- 전체 플로우를 목업 없이 테스트할 수 있습니다.

---

## 프로덕션 빌드

```bash
npm run build
npm start
```

---

## 검사 및 테스트

```bash
npm run lint         # ESLint
npm run lint:words   # 금지어 검사 (필수!)
```

---

## 문서

**모든 개발자는 먼저 `docs/README.md`를 읽으세요.**

주요 문서:
- **[docs/README.md](./docs/README.md)** — 문서 인덱스 및 전체 가이드
- **[docs/structure.md](./docs/structure.md)** — 폴더 구조
- **[docs/architecture.md](./docs/architecture.md)** — 기술 스택, 데이터 흐름
- **[docs/ui/](./docs/ui/)** — 디자인 시스템 (3-Tone, 목업, Mock 시나리오)
- **[docs/policies/](./docs/policies/)** — 금지어, 위기 감지 정책

---

## 핵심 정책

1. **금지어 차단**: `npm run lint:words` 필수
2. **위기 감지**: 입력에서 "때리", "자살" 등 감지 시 모달 표시
3. **디자인 톤**: Tone L (입력), Tone P (결과), Tone Q (고급) — 절대 섞지 않기
4. **목업 우선**: `design/mockups/XX-XXX/` 폴더에 파일 있으면 목업 기반 구현

---

## 배포 전 체크리스트

- [ ] `npm run lint:words` 통과
- [ ] `npm run build` 성공
- [ ] 전체 플로우 수동 테스트 (온보딩 → 세션 → 채팅 → 결과)
- [ ] 모바일 반응형 확인

---

자세한 개발 가이드는 **[docs/README.md](./docs/README.md)** 를 참고하세요.

