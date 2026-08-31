---
title: L1 Context — backend
last_updated: 2026-08-31
---

# L1 Context — backend

다시봄 백엔드(Spring Boot 3.3 + Java 21 + MariaDB) 내부 문서.

서비스 전체 정책은 `docs/shared/70-policy/` 에 있다.

## 빠른 시작

```bash
cd backend
./gradlew bootRun        # localhost:8080
./gradlew test
```

로컬 절차: [`../env/60-runtime/local-dev.md`](../env/60-runtime/local-dev.md).

| 계층 | 경로 |
|---|---|
| 30 components | [30-components/](30-components/) |
| 40 data | [40-data.md](40-data.md) |
| 50 api | [50-api.md](50-api.md) |
| 60 runtime | [60-runtime/](60-runtime/) |
| 70 policy | [70-policy.md](70-policy.md) |
