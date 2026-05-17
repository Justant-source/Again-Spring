plugins {
    java
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.againspring"
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.4.1")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.5")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    implementation("org.yaml:snakeyaml:2.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    implementation("org.springframework.boot:spring-boot-starter-logging")
    // logback-classic은 spring-boot-starter-test가 이미 관리 버전(1.5.x)으로 포함함
    // 명시적 1.4.11 고정은 StatusPrinter2 ClassNotFoundException 유발하므로 제거

    implementation("com.github.vladimir-bukhtoyarov:bucket4j-core:7.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    val testcontainersVersion = "1.20.4"
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:mariadb:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testRuntimeOnly("com.h2database:h2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        excludeTags("haiku") // 실 haiku 호출 테스트는 기본 test 태스크에서 제외
    }
    environment("TESTCONTAINERS_RYUK_DISABLED", "false")
    jvmArgs("-Dapi.version=1.44")
}

// 실 Claude CLI haiku 스모크: ./gradlew haikuSmoke (호스트 ~/.claude 로그인 전제)
tasks.register<Test>("haikuSmoke") {
    group = "verification"
    description = "ClaudeCodeBridge 실제 haiku 호출 스모크 (llm.provider=claude-code)"
    useJUnitPlatform {
        includeTags("haiku")
    }
    systemProperty("spring.profiles.active", "haiku-smoke")
}
