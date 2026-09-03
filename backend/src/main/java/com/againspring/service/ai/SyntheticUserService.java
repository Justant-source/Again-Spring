package com.againspring.service.ai;

import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** AI-user(synthetic) 계정의 유일한 쓰기 경로. orchestrator의 users 직접 INSERT/UPDATE를 대체한다(2026-09). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyntheticUserService {

    public record PersonaUpsertRequest(String id, String email, String nickname, String password) {}
    public record PersonaUpsertResponse(String id, String status) {}

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PersonaUpsertResponse upsert(PersonaUpsertRequest req) {
        User existing = userRepository.findById(req.id()).orElse(null);
        if (existing != null && existing.getDeletedAt() != null) {
            log.info("[SyntheticUser] {} is soft-deleted; not resurrecting", req.id());
            return new PersonaUpsertResponse(req.id(), "DELETED_SKIPPED");
        }
        Instant now = Instant.now();
        if (existing == null) {
            User u = new User();
            u.setId(req.id());
            u.setEmail(req.email());
            u.setNickname(req.nickname());
            u.setPasswordHash(passwordEncoder.encode(req.password()));
            u.setRoles(List.of("USER"));
            u.setSynthetic(true);
            u.setStatus("ACTIVE");
            u.setGuest(false);
            u.setMustChangePassword(false);
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            userRepository.save(u);
            return new PersonaUpsertResponse(req.id(), "CREATED");
        }
        existing.setNickname(req.nickname());
        existing.setPasswordHash(passwordEncoder.encode(req.password()));
        existing.setSynthetic(true);
        existing.setStatus("ACTIVE");
        existing.setMustChangePassword(false);
        existing.setUpdatedAt(now);
        userRepository.save(existing);
        return new PersonaUpsertResponse(req.id(), "UPDATED");
    }

    /** 삭제되지 않은 synthetic 계정 전체의 비밀번호를 회전한다 (AI_USER_BOT_PASSWORD 변경 시). */
    @Transactional
    public int rotatePassword(String password) {
        String hash = passwordEncoder.encode(password);
        return userRepository.rotateSyntheticPasswordHash(hash, Instant.now());
    }
}
