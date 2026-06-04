# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY src src/
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# Runtime stage — needs Node.js + Claude Code CLI for subprocess invocation
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache nodejs npm && \
    npm install -g @anthropic-ai/claude-code
COPY --from=build /app/build/libs/llm-ai-user-*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
