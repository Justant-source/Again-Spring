package com.againspring.service.community;

import com.againspring.domain.community.PostView;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.PostViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사연 조회수 기록 서비스
 * device_id 기준 중복 조회 방지 — DB unique constraint가 최종 안전망
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
    @Transactional
    public long recordViewAndGetCount(String postId, String deviceId) {
        try {
            PostView view = PostView.builder()
                    .postId(postId)
                    .deviceId(deviceId)
                    .build();
            postViewRepository.save(view);
            postRepository.incrementViewCount(postId);
            log.debug("View recorded: post={}, device={}", postId, deviceId);
        } catch (DataIntegrityViolationException ignored) {
            // 동일 디바이스 재조회 — 카운트 증가 없이 무시
        }
        return postRepository.findById(postId)
                .map(p -> p.getViewCount().longValue())
                .orElse(0L);
    }
}
