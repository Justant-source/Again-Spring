package com.againspring.service.community;

import com.againspring.domain.community.PostView;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.PostViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 사연 조회수 기록 서비스
 * device_id 기준 중복 조회 방지 — DB unique constraint가 최종 안전망
 *
 * NOTE: @Transactional 제거 — outer transaction 내에서 DataIntegrityViolationException을
 * catch해도 JPA가 이미 트랜잭션을 rollback-only로 표시하여 UnexpectedRollbackException이
 * 발생하는 Spring JPA 특성 때문. 각 레포지토리 메서드가 자체 트랜잭션을 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewService {

    private final PostViewRepository postViewRepository;
    private final PostRepository postRepository;

    /**
     * 조회 기록 후 현재 조회수 반환
     * 동일 device_id의 재조회는 카운트 증가 없이 현재 값 반환
     */
    public long recordViewAndGetCount(String postId, String deviceId) {
        if (!postViewRepository.existsByPostIdAndDeviceId(postId, deviceId)) {
            try {
                postViewRepository.saveAndFlush(PostView.builder()
                        .postId(postId)
                        .deviceId(deviceId)
                        .build());
                postRepository.incrementViewCount(postId);
                log.debug("View recorded: post={}, device={}", postId, deviceId);
            } catch (DataIntegrityViolationException ignored) {
                // race condition: 동시 요청으로 중복 insert — 무시
            }
        }
        return postRepository.findById(postId)
                .map(p -> p.getViewCount().longValue())
                .orElse(0L);
    }
}
