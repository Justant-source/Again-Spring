# Again Spring Backend Deployment Guide

**한국어 버전**: 배포 가이드 (아래)

---

## Quick Start (Development)

### 1. Local Development

**Prerequisites**: Java 21, MongoDB, Neo4j

```bash
cd backend

# Configure environment
export MONGO_URI="mongodb://localhost:27017/againspring"
export NEO4J_URI="bolt://localhost:7687"
export JWT_SECRET="dev-secret-change-in-production"

# Run
./gradlew bootRun
```

Access Swagger UI: http://localhost:8080/swagger-ui.html

### 2. Local Docker Compose

**Prerequisites**: Docker, Docker Compose

Create `.env` file:

```env
MONGO_URI=mongodb://mongo:27017/againspring
NEO4J_URI=bolt://neo4j:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=changepassword
JWT_SECRET=dev-secret
LLM_PROVIDER=claude-code
SPRING_PROFILES_ACTIVE=dev
```

```bash
# Assuming docker-compose.yml exists at project root
docker-compose up
```

### 3. Docker Build

```bash
cd backend

# Build image
docker build -t againspring-backend:latest -f docker/Dockerfile .

# Run container
docker run -d \
  --name againspring \
  -p 8080:8080 \
  -e MONGO_URI="mongodb://mongo:27017/againspring" \
  -e NEO4J_URI="bolt://neo4j:7687" \
  -e JWT_SECRET="your-secret" \
  againspring-backend:latest
```

---

## Production Deployment

### Environment Variables

**Required**:

```bash
MONGO_URI              # MongoDB connection string
NEO4J_URI              # Neo4j Bolt connection
NEO4J_USER             # Neo4j username
NEO4J_PASSWORD         # Neo4j password
JWT_SECRET             # JWT signing secret (≥32 chars, random)
SPRING_PROFILES_ACTIVE # Set to "prod"
```

**Optional**:

```bash
LLM_PROVIDER          # "claude-code" (default) or "claude-api"
ANTHROPIC_API_KEY     # If using claude-api provider
JAVA_OPTS             # JVM tuning: -Xms1g -Xmx2g -XX:+UseG1GC
CLAUDE_LOGIN_STATUS   # "ok" if Claude CLI pre-authenticated
```

### JVM Tuning

For production workloads (50+ concurrent sessions):

```bash
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled"
```

### Database Connection Pooling

Configure in application-prod.yml:

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGO_URI}
      # Implicit pooling, tuning via URI options
    neo4j:
      uri: ${NEO4J_URI}
      pool:
        metrics-enabled: true
        max-connection-pool-size: 50
```

### Scaling Notes

#### Horizontal Scaling

The backend is **stateless** and can scale horizontally:

```bash
# Kubernetes example
kubectl scale deployment againspring-backend --replicas=3
```

Each instance:
- Connects independently to MongoDB + Neo4j
- No shared session state
- Load balancer distributes traffic

#### Vertical Scaling

JVM heap per instance:

| Concurrent Sessions | Heap Size | Pod/VM |
|---|---|---|
| 10-20 | 512MB | t3.small |
| 20-50 | 1-2GB | t3.medium |
| 50-100 | 2-4GB | t3.large |
| 100+ | 4-8GB | t3.xlarge |

### Health Checks

Health check endpoint:

```bash
# Kubernetes probe
curl http://localhost:8080/actuator/health
```

Response indicates database connectivity.

### Monitoring

Enable Actuator endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

Prometheus metrics available at:
- http://localhost:8080/actuator/prometheus

### SSL/TLS

In production, use reverse proxy (nginx, HAProxy, AWS ALB):

```nginx
# Nginx config
upstream backend {
    server againspring-backend:8080;
}

server {
    listen 443 ssl;
    server_name api.againspring.app;

    ssl_certificate /etc/ssl/certs/cert.pem;
    ssl_certificate_key /etc/ssl/private/key.pem;

    location / {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Troubleshooting

### Backend won't start

```bash
# Check logs
docker logs againspring

# Verify env vars
env | grep MONGO_URI
env | grep NEO4J_URI
env | grep JWT_SECRET
```

### Database connection failure

```bash
# Test MongoDB
mongosh "mongodb://your-uri"

# Test Neo4j
cypher-shell -a "bolt://your-uri" -u neo4j -p password "RETURN 1"
```

### Claude CLI not found

```bash
# Inside container
npm install -g @anthropic-ai/claude-code
claude --version
```

### High memory usage

```bash
# Increase heap
export JAVA_OPTS="-Xmx4g"
docker run -e JAVA_OPTS="-Xmx4g" ...
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/deploy.yml
name: Deploy Backend

on:
  push:
    branches: [main]
    paths: [backend/**]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Build
        run: ./backend/gradlew build
      - name: Build Docker Image
        run: |
          docker build -t againspring-backend:${{ github.sha }} backend/
      - name: Push to Registry
        run: |
          docker push registry.example.com/againspring-backend:${{ github.sha }}
```

---

## References

- Full deployment guide: `shared/docs/DEPLOYMENT.md`
- API docs: http://localhost:8080/swagger-ui.html
- Database schema: `shared/docs/DATABASE_SCHEMA.md`

---

## 한국어 배포 가이드

### 개발 환경

```bash
export MONGO_URI="mongodb://localhost:27017/againspring"
export NEO4J_URI="bolt://localhost:7687"
export JWT_SECRET="dev-secret"
./gradlew bootRun
```

### 프로덕션 환경 변수

필수:
- `MONGO_URI`: MongoDB 연결 문자열
- `NEO4J_URI`: Neo4j Bolt 연결
- `JWT_SECRET`: JWT 서명 시크릿 (32자 이상, 랜덤)

선택:
- `JAVA_OPTS`: JVM 옵션 (힙 메모리 등)
- `LLM_PROVIDER`: "claude-code" 또는 "claude-api"

### 스케일링

상태를 저장하지 않으므로 수평 확장 가능. 로드 밸런서로 트래픽 분산.

### 모니터링

```bash
curl http://localhost:8080/actuator/health
```

---

**Version**: 0.1.0  
**Last Updated**: 2026-04-24
