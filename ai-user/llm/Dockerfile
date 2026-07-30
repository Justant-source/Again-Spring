# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY src src/
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# Runtime stage — both CLIs are invoked only for a request. They do not keep
# conversational state in the worker process.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Codex CLI ships a native binary. Use glibc-based Debian rather than Alpine
# (musl) so its runtime ABI is supported inside the container.
RUN apt-get update && apt-get install -y --no-install-recommends nodejs npm ca-certificates wget bubblewrap && \
    npm install -g @anthropic-ai/claude-code @openai/codex
COPY --from=build /app/build/libs/llm-ai-user-*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
