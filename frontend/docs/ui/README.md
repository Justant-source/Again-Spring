# UI & 디자인 문서

프론트엔드의 디자인 시스템, 목업 통합, Mock API 시나리오에 대한 가이드입니다.

---

## 목차

1. **[design-handoff.md](./design-handoff.md)**
   - 3-Tone Hybrid 디자인 시스템 (Tone L/P/Q)
   - 컴포넌트 매핑 테이블
   - 색상, 타이포, 간격 토큰
   - 목업 파일 위치 및 반영 상태 관리
   - 목업 업데이트 절차

2. **[mock-scenarios.md](./mock-scenarios.md)**
   - 5가지 Mock API 시나리오 (사실형, 차이형, 혼합형, Solo, 경고형)
   - 턴별 입력·응답 샘플
   - 최종 리포트 JSON 구조
   - MSW Handler 구현 예시
   - 개발 단계 디버깅 팁

---

## 빠른 시작

### 새 화면 구현할 때

1. `design/mockups/XX-XXX/` 폴더 확인
2. 목업 있으면 → **design-handoff.md**의 Tone 선택
3. 파일 상단에 상태 주석 추가 (`APPLIED`, `PENDING`, `PARTIAL`)

### Mock API 테스트할 때

- **mock-scenarios.md**의 5가지 시나리오 참고
- `npm run dev`로 MSW 자동 활성화 (dev 환경만)
- `?mockScenario=factual` 쿼리로 특정 시나리오 강제 가능

---

## 핵심 원칙

- **3-Tone 분리**: Tone L (입력), Tone P (결과), Tone Q (고급)를 절대 섞지 않기
- **절대 금지어**: "과실비율", "판결", "가해자/피해자" 등 판결 용어 금지
- **프라이버시**: 갈등 원문 내용을 시각화에 노출하지 않기
- **목업 우선**: 목록 폴더에 파일 있으면 목업 기반 구현, 없으면 기본 디자인 + PENDING 주석

