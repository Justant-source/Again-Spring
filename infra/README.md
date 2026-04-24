# 다시봄 인프라

로컬 개발 환경용 MongoDB 7 및 Neo4j 5 Docker Compose 설정.

## 빠른 시작

```bash
# .env 파일 작성
cp .env.example .env
# MONGO_PASSWORD, NEO4J_PASSWORD, JWT_SECRET 설정

# 서비스 시작
docker compose up -d

# 상태 확인
docker compose ps
docker compose logs -f
```

## 접근 방법

- **MongoDB**: `mongodb://admin:changeme@localhost:27017/againspring?authSource=admin`
- **Neo4j 브라우저**: http://localhost:7474 (사용자: neo4j, 비밀번호: `.env`의 `NEO4J_PASSWORD`)
- **Neo4j Bolt**: `bolt://localhost:7687`

## 정리

```bash
# 컨테이너 및 볼륨 제거
docker compose down -v
```

## 프로덕션 배포

프로덕션 환경에서는 `-f docker-compose.prod.yml` 오버레이를 적용:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

이 설정은 포트 노출을 최소화하고, 기본 비밀번호를 제거하며, 리소스 제한을 적용합니다.

## Cloudflare Tunnel

원격 접근은 `cloudflare/` 디렉토리의 설정으로 관리합니다 (현재 TBD).
