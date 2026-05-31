package com.againspring.service.marketing.social;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.SocialPublishResult;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.SocialPublishResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 소셜 플랫폼 발행 조율 서비스
 * 중복 검사, 상태 전환, 비동기 실행 호출
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SocialPublishService {

    @Value("${app.social.publishing-enabled:true}")
    private boolean publishingEnabled;

    private final MarketingContentRepository contentRepository;
    private final SocialPublishResultRepository resultRepository;
    private final SocialPublishExecutor executor;

    /**
     * 소셜 플랫폼 발행 시작 (멱등성 보장)
     * 1. 발행 활성화 확인
     * 2. 중복 발행 방지 (이미 성공한 플랫폼)
     * 3. 콘텐츠 상태 → PUBLISHING
     * 4. PENDING 결과 레코드 생성
     * 5. 비동기 실행 위임
     */
    public List<SocialPublishResult> initiatePublish(Long contentId, List<String> targets, String linkMode) {
        if (!publishingEnabled) {
            throw new DuplicatePublishException("소셜 발행이 비활성화되어 있습니다 (app.social.publishing-enabled=false)");
        }

        // 중복 검사: 이미 성공한 플랫폼은 재발행 불가
        for (String targetPlatform : targets) {
            resultRepository.findByContentIdAndPlatform(contentId, MarketingContent.Platform.valueOf(targetPlatform))
                    .filter(r -> r.getState() == SocialPublishResult.ResultState.SUCCEEDED)
                    .ifPresent(r -> {
                        throw new DuplicatePublishException("이미 발행 완료된 플랫폼입니다: " + targetPlatform);
                    });
        }

        // 콘텐츠 상태 → PUBLISHING
        MarketingContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));
        content.setStatus(MarketingContent.Status.PUBLISHING);
        contentRepository.save(content);

        // PENDING 결과 레코드 생성 또는 업데이트
        List<SocialPublishResult> results = new ArrayList<>();
        for (String targetPlatform : targets) {
            SocialPublishResult result = resultRepository
                    .findByContentIdAndPlatform(contentId, MarketingContent.Platform.valueOf(targetPlatform))
                    .orElseGet(() -> SocialPublishResult.builder()
                            .contentId(contentId)
                            .platform(MarketingContent.Platform.valueOf(targetPlatform))
                            .build());
            result.setState(SocialPublishResult.ResultState.PENDING);
            results.add(resultRepository.save(result));
        }

        // 비동기 실행
        executor.executePublish(contentId, targets, linkMode);
        log.info("[SOCIAL_PUBLISH] Initiated for contentId={} targets={}", contentId, targets);

        return results;
    }
}
