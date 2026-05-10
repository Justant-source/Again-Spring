package com.againspring.service;

import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 화이트리스트(app.admin-emails)에 등록된 이메일에 대해 ADMIN 역할을 자동 부여한다.
 * - 가입/로그인/OAuth 콜백마다 호출 (idempotent — 이미 ADMIN이면 no-op)
 * - 탈퇴 후 재가입 시에도 일관되게 ADMIN 유지
 *
 * 운영 변경:
 *   - dev/prod application.yml의 ADMIN_EMAILS 환경변수 (콤마 구분)
 *   - 예: ADMIN_EMAILS=admin1@example.com,admin2@example.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminRoleAssigner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;

    @Value("${app.admin-emails:}")
    private String adminEmailsCsv;

    private Set<String> adminEmails() {
        if (adminEmailsCsv == null || adminEmailsCsv.isBlank()) return Set.of();
        return Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * 화이트리스트 이메일이면 ADMIN 역할 부여 후 저장. 변경 없으면 그대로 반환.
     * 게스트는 이메일 없으므로 항상 no-op.
     */
    public User ensureAdminIfWhitelisted(User user) {
        if (user == null || user.getEmail() == null) return user;
        if (!adminEmails().contains(user.getEmail().toLowerCase())) return user;

        List<String> roles = user.getRoles();
        if (roles == null) roles = new ArrayList<>();
        if (roles.contains(ADMIN_ROLE)) return user;

        Set<String> next = new HashSet<>(roles);
        next.add(ADMIN_ROLE);
        user.setRoles(new ArrayList<>(next));
        User saved = userRepository.save(user);
        log.info("Granted ADMIN role to {}", user.getEmail());
        return saved;
    }
}
