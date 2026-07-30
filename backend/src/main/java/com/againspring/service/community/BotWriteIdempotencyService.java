package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.ai.BotRequestDedup;
import com.againspring.repository.UserRepository;
import com.againspring.repository.ai.BotRequestDedupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Atomically maps an internal AI-user write key to its resulting entity.
 * Headers supplied by regular users are deliberately ignored: this is not a
 * public API idempotency contract.
 */
@Service
@RequiredArgsConstructor
public class BotWriteIdempotencyService {

    public enum TargetType { POST, COMMENT }
    public record Execution<T>(T target, boolean created) {}

    private final BotRequestDedupRepository dedupRepository;
    private final UserRepository userRepository;

    public boolean appliesTo(String userId, String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                && userId != null
                && userRepository.findById(userId).map(user -> user.isSynthetic()).orElse(false);
    }

    @Transactional
    public <T> Execution<T> execute(String botUserId,
                         String idempotencyKey,
                         TargetType targetType,
                         Supplier<T> create,
                         Function<String, T> findExisting) {
        validateKey(idempotencyKey);
        int claimed = dedupRepository.claim(idempotencyKey, targetType.name(), botUserId);
        if (claimed == 1) {
            T created = create.get();
            String targetId = extractTargetId(created);
            BotRequestDedup mapping = dedupRepository.findById(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency mapping disappeared"));
            mapping.setTargetId(targetId);
            return new Execution<>(created, true);
        }

        BotRequestDedup mapping = dedupRepository.findById(idempotencyKey)
                .orElseThrow(() -> new BusinessException("IDEMPOTENCY_MAPPING_MISSING", "Idempotency mapping is unavailable", 409));
        if (!botUserId.equals(mapping.getBotUserId()) || !targetType.name().equals(mapping.getTargetType())) {
            throw new BusinessException("IDEMPOTENCY_KEY_CONFLICT", "Idempotency key is already bound to another bot write", 409);
        }
        if (mapping.getTargetId() == null) {
            // INSERT IGNORE normally cannot reach here while the initial
            // transaction is in progress. Keep the failure explicit if a
            // manually damaged row is encountered.
            throw new BusinessException("IDEMPOTENCY_TARGET_UNAVAILABLE", "Idempotency target is not available", 409);
        }
        T existing = findExisting.apply(mapping.getTargetId());
        if (existing == null) {
            throw new BusinessException("IDEMPOTENCY_TARGET_MISSING", "Idempotency target no longer exists", 409);
        }
        return new Execution<>(existing, false);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 160 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-160 URL-safe characters", 400);
        }
    }

    private String extractTargetId(Object target) {
        if (target instanceof com.againspring.domain.community.Post post) return post.getId();
        if (target instanceof com.againspring.domain.community.PostComment comment) return String.valueOf(comment.getId());
        throw new IllegalArgumentException("Unsupported idempotency target: " + target.getClass().getName());
    }
}
