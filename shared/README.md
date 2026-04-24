# Shared — 다시봄 (Again Spring) 모노레포 공유 자원

다시봄 모노레포의 공유 자원 모음. FE와 BE가 함께 참고하는 타입, 문서, 프롬프트, 스키마가 포함됩니다.

## 📁 디렉토리 구조

### `docs/` — 문서 (18개)

**백엔드 문서**:
- `API_SPEC.md` — REST API 명세서
- `DATABASE_SCHEMA.md` — MongoDB 스키마 정의
- `LLM_BRIDGE_ARCHITECTURE.md` — LLM 연동 아키텍처
- `BACKEND_WORK_ORDER.md` — 백엔드 작업 명세
- `BACKEND_README.md` — 백엔드 설정 및 구성 가이드
- `PROJECT_STRUCTURE_UPDATE.md` — 프로젝트 구조 변경 문서
- `DEPLOYMENT.md` — 배포 가이드
- `INTEGRATION_TESTING.md` — 통합 테스트 전략

**프론트엔드 문서**:
- `WORK_ORDER.md` — 프론트엔드 작업 명세
- `COMMAND_README.md` — 프론트엔드 구성 가이드
- `MOCKUP_INTEGRATION.md` — 목업 통합 가이드
- `MOCK_SCENARIOS.md` — Mock API 시나리오

**공통 문서**:
- `SYSTEM_PROMPTS.md` — AI 중재자 시스템 프롬프트 원본
- `CATEGORIES.md` — 갈등 카테고리 체계
- `FORBIDDEN_WORDS.md` — 금지 단어 목록
- `ONBOARDING_MAPPING.md` — 온보딩 질문 매핑
- `RATIO_CALCULATION.md` — 화해 기여도 산출 알고리즘
- `TERMS_OF_SERVICE.md` — 서비스 약관

---

### `prompts/` — AI 중재자 프롬프트

#### `system.md` (1개)
- `system.md` — 중재자 정체성, 핵심 원칙, 절대 금기, 말투, 위험 감지 프로토콜

#### `gottman/` (4개) — Gottman 이론 지식 베이스
- `four_horsemen.md` — 관계 파괴 4가지 패턴 및 해독제
- `bids_and_repair.md` — 연결 요청과 화해 제스처
- `sound_relationship_house.md` — 관계 건강도 7층 구조 및 5:1 매직 레이션

#### `nvc/` (1개) — 비폭력대화 템플릿
- `four_steps.md` — NVC 4단계 (관찰, 느낌, 욕구, 부탁)

#### `relations/` (4개) — 관계 유형별 가이드
- `couple.md` — 연인/부부 관계 (Four Horsemen, Love Maps, Bids)
- `friend.md` — 친구 관계 (기대치, 경계, 상호성)
- `family.md` — 가족 관계 (세대 차이, 역사 맥락, 용서)
- `parent_child.md` — 부모-자식 관계 (권력 비대칭, 경계, 자율성)

#### `turns/` (8개) — 턴별 중재 지시
- `turn_1_a.md` — A의 첫 입력 (카테고리 확인, 위험 감지, 중립 요약)
- `turn_2_b.md` — B의 첫 입력 (위험 감지, 중립 요약, 갈등 유형 분류)
- `turn_3_a.md` — A에게 심화 질문 2개 (관점, 감정/욕구)
- `turn_4_b.md` — B에게 심화 질문 2개 (A 답변 기반)
- `turn_5_a.md` — A에게 조망수용 요청 (선택 턴)
- `turn_6_b.md` — B에게 조망수용 요청 (선택 턴)
- `solo_mode.md` — 혼자하는 중재 모드 (B 미수락/선택 시)

---

### `types/` — TypeScript 타입 정의 (4개)

- `session.ts` — Session, Turn, SessionStatus, ConflictType, RelationType 등
- `user.ts` — User, CommunicationStyle, TemperatureEntry
- `report.ts` — Report, HorsemenDetection, NVCScript, NeedsMapPayload 등
- `common.ts` — RelationType, ConflictType, CommunicationStyle (공유 타입)

**출처**: `/frontend/lib/types/` 의 canonical 타입을 복사한 것. 동기화 필요 시 FE 타입을 먼저 수정.

---

### `schemas/` — 스키마 정의 (2개)

- `openapi.yaml` — REST API OpenAPI 3.0.3 명세 (placeholder)
- `mongodb-schemas.json` — MongoDB 컬렉션 스키마 (placeholder)

**참고**: 각 파일의 주석에서 `shared/docs/`의 원본 문서와의 동기화 필요성 명시.

---

## 🔄 유지보수

- **SYSTEM_PROMPTS.md**: 원본은 `.request/command/`에 있음. 업데이트 후 `shared/prompts/` 파일들과 동기화 필요.
- **타입**: `/frontend/lib/types/`가 canonical 소스. 변경 시 `shared/types/`도 업데이트.
- **문서**: 각 `.request/` 디렉토리가 업데이트되면 `shared/docs/`에도 복사.

---

## 📊 파일 수량

| 카테고리 | 개수 |
|--------|------|
| docs | 18 |
| prompts/system | 1 |
| prompts/gottman | 3 |
| prompts/nvc | 1 |
| prompts/relations | 4 |
| prompts/turns | 8 |
| types | 4 |
| schemas | 2 |
| **합계** | **41** |
