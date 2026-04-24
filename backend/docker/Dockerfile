# Multi-stage Dockerfile for Again Spring Backend
# Stage 1: Build (Gradle)
# Stage 2: Runtime (Alpine JRE + Claude CLI)

# ============================================================
# Stage 1: Build
# ============================================================
FROM gradle:8.10-jdk21 AS builder

WORKDIR /build

# Copy build configuration
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle ./gradle
COPY gradlew .
COPY gradlew.bat .

# Copy source
COPY src ./src

# Build JAR
RUN chmod +x ./gradlew && \
    ./gradlew clean bootJar --no-daemon && \
    mv build/libs/*.jar /app.jar

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Again Spring Team"
LABEL version="0.1.0"

WORKDIR /app

# Install runtime dependencies
# - curl: for health checks
# - bash: for entrypoint script
# - nodejs/npm: for Claude CLI
RUN apk add --no-cache \
    curl \
    bash \
    nodejs \
    npm

# Install Claude CLI
RUN npm install -g @anthropic-ai/claude-code

# Create non-root user
RUN addgroup -g 1000 againspring && \
    adduser -D -u 1000 -G againspring againspring

# Copy JAR from builder
COPY --from=builder /app.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh

# Set permissions
RUN chmod +x /app/entrypoint.sh && \
    chown -R againspring:againspring /app

# Switch to non-root user
USER againspring

# Default environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
