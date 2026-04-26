# shared — FE+BE 공유 자산

다시봄 모노레포의 공통 문서, 타입, 스키마.

## 주요 디렉토리

- **`docs/`** — FE와 BE가 함께 참고하는 정책·API·아키텍처 문서
  - `docs/README.md` 참고: 정책(policies/), API(api/), 프롬프트(prompts/), 구조(structure.md), 아키텍처(architecture.md)
  
- **`docs/prompts/`** — LLM 중재자 프롬프트 (BE 런타임 로딩)
  - system.md, gottman/, nvc/, relations/, turns/

- **`types/`** — TypeScript 공유 타입 (Session, User, Report 등)

- **`schemas/`** — API/DB 스키마 정의 (OpenAPI, MariaDB)

## 시작하기

**정책, API, 아키텍처 문서는 모두 `shared/docs/`에 집중됩니다.**

→ [shared/docs/README.md](./docs/README.md) 참고
