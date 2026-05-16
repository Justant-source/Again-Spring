# Visual Reference

> 다시봄의 현재 디자인 상태 캡처. Claude Design 호출 시 "이 톤으로 만들어줘" 컨텍스트로 제공.

---

## 캡처 목록

| 캡처 파일 | 화면 | 톤 | 캡처 날짜 |
|---|---|---|---|
| `landing-tone-l.png` | `/` 랜딩 | L | Phase 3에서 추가 |
| `onboarding-modal-tone-l.png` | 30초 온보딩 모달 (신규 사용자 첫 진입) | L | Phase 3에서 추가 |
| `chat-solo-tone-l.png` | Solo 채팅 세션 | L | Phase 3에서 추가 |
| `chat-duo-tone-l.png` | Duo 채팅 세션 | L | Phase 3에서 추가 |
| `result-solo-tone-p.png` | V12 Solo 리포트 | P | Phase 3에서 추가 |

---

## 갱신 정책

화면 디자인이 **크게 변경**될 때 해당 캡처 재생성:
- 톤 변경, 레이아웃 구조 변경, 신규 컴포넌트 추가

**갱신 불요**:
- 색·간격·라운드 미세 조정
- 텍스트 카피만 변경

---

## 캡처 추가 방법

1. dev 서버(`npm run dev`) 실행
2. 해당 화면 → 브라우저 DevTools 모바일 뷰 (360px×780px 기준)
3. 스크린샷 저장 (PNG, 가능하면 2x 해상도)
4. 이 폴더에 추가 + 위 표 갱신

---

*캡처 PNG는 Phase 3 (V14)에서 추가됩니다.*
