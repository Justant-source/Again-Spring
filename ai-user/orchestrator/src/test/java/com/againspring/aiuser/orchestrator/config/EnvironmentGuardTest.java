package com.againspring.aiuser.orchestrator.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentGuardTest {

    private static final String PROD_DB = "jdbc:mariadb://againspring-mariadb-prod:3306/againspring?useUnicode=true";
    private static final String DEV_DB = "jdbc:mariadb://againspring-mariadb-dev:3306/againspring_dev?useUnicode=true";

    @Test
    void prodEnvWithProdHostsPasses() {
        assertEquals(EnvironmentGuard.Env.PROD,
            EnvironmentGuard.validate("prod", PROD_DB, "http://againspring-backend-prod:8080"));
    }

    @Test
    void devEnvWithDevHostsPasses() {
        assertEquals(EnvironmentGuard.Env.DEV,
            EnvironmentGuard.validate("dev", DEV_DB, "http://againspring-backend-dev:8080"));
    }

    @Test
    void missingEnvFailsClosed() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> EnvironmentGuard.validate(null, PROD_DB, "http://againspring-backend-prod:8080"));
        assertTrue(e.getMessage().contains("AI_USER_ENV"));
    }

    @Test
    void crossWiredHostsFail() {
        assertThrows(IllegalStateException.class,
            () -> EnvironmentGuard.validate("dev", PROD_DB, "http://againspring-backend-dev:8080"));
        assertThrows(IllegalStateException.class,
            () -> EnvironmentGuard.validate("prod", PROD_DB, "http://againspring-backend-dev:8080"));
    }

    @Test
    void blankBackendUrlFails() {
        assertThrows(IllegalStateException.class,
            () -> EnvironmentGuard.validate("prod", PROD_DB, ""));
    }
}
