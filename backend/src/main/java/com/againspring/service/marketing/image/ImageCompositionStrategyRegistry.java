package com.againspring.service.marketing.image;

import com.againspring.domain.marketing.MarketingContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that maps Platform → ImageCompositionStrategy.
 * Spring auto-injects all ImageCompositionStrategy beans.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class ImageCompositionStrategyRegistry {

    private final Map<MarketingContent.Platform, ImageCompositionStrategy> registry;

    public ImageCompositionStrategyRegistry(List<ImageCompositionStrategy> strategies) {
        this.registry = strategies.stream()
                .collect(Collectors.toMap(ImageCompositionStrategy::supports, Function.identity()));
    }

    @PostConstruct
    void log() {
        log.info("ImageCompositionStrategyRegistry initialized with platforms: {}", registry.keySet());
    }

    public Optional<ImageCompositionStrategy> find(MarketingContent.Platform platform) {
        return Optional.ofNullable(registry.get(platform));
    }
}
