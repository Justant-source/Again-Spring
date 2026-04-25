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

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/build/libs/againspring-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
