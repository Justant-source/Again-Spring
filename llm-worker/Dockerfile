# ── Build stage ──
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY src src/

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# ── Runtime stage ──
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Node.js + Claude Code CLI (구독 기반 인증, ~/.claude 마운트 필요)
RUN apk add --no-cache nodejs npm && \
    npm install -g @anthropic-ai/claude-code

COPY --from=build /app/build/libs/llm-worker-*.jar app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
