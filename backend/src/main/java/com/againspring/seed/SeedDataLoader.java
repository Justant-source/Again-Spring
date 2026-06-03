package com.againspring.seed;

import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 시드 데이터 로더 (dev 프로파일)
 * 애플리케이션 시작 시 자동 실행
 * 3중 가드: 설정 + 프로파일 + 이미 로드됨 체크
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader implements CommandLineRunner {

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 가드 1: 설정에서 비활성화됨
        if (!seedEnabled) {
            log.info("Seed disabled by app.seed.enabled=false. Skipping.");
            return;
        }

        // 가드 2: dev 프로파일이 아님
        if (!activeProfiles.contains("dev")) {
            log.warn("Seed guard: not dev profile (active={}). Aborting seed load.", activeProfiles);
            return;
        }

        // 가드 3: 이미 로드됨
        if (userRepository.existsByEmail("test1@again.com")) {
            log.info("Test data already seeded (test1@again.com exists). Skipping.");
            return;
        }

        log.info("=== Starting dev seed data load ===");

        // Step 1: 페르소나 생성 및 저장
        List<User> users = SeedPersonas.build(passwordEncoder);
        users.forEach(userRepository::save);
        log.info("✓ Seeded {} personas", users.size());

        // Step 2: 시나리오 빌드는 제거됨 (SeedScenarios, SeedScenarioBuilder 삭제)
        log.info("Scenario seeding disabled (legacy code removed).");

        log.info("=== Dev seed data load complete ===");
    }
}
