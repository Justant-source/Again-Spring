package com.againspring.aiuser.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiUserOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiUserOrchestratorApplication.class, args);
    }
}
