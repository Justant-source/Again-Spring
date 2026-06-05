package com.againspring.service.admin;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 사용자 익명화 서비스
 * PII(개인식별정보) 제거: 이메일, 비밀번호, OAuth 정보, 닉네임
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAnonymizationService {

    private final UserRepository userRepository;

    /**
     * 사용자 데이터 익명화
     * - email 제거
     * - passwordHash 제거
     * - provider, providerId 제거
     * - nickname을 "삭제된 사용자"로 변경
     * - deletedAt 설정
     *
     * @param userId 익명화할 사용자 ID
     */
    @Transactional
    public void anonymize(String userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404));

        // PII 제거
        user.setEmail(null);
        user.setPasswordHash(null);
        user.setProvider(null);
        user.setProviderId(null);
        user.setNickname("삭제된 사용자");
        user.setDeletedAt(Instant.now());

        userRepository.save(user);

        log.info("User anonymized: userId={}", userId);
    }
}
