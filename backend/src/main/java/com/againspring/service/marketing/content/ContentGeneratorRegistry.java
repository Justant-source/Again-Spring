package com.againspring.service.marketing.content;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.againspring.domain.marketing.MarketingContent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry that maps each Platform to its ContentGenerator bean.
 * Spring auto-collects all ContentGenerator implementations via list injection.
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Slf4j
public class ContentGeneratorRegistry {

    private final Map<MarketingContent.Platform, ContentGenerator> registry;

    public ContentGeneratorRegistry(List<ContentGenerator> generators) {
        this.registry = generators.stream()
                .collect(Collectors.toMap(ContentGenerator::supports, Function.identity()));
    }

    @PostConstruct
    public void validate() {
        log.info("ContentGeneratorRegistry initialized with {} generators: {}",
                registry.size(), registry.keySet());
    }

    public ContentGenerator resolve(MarketingContent.Platform platform) {
        ContentGenerator generator = registry.get(platform);
        if (generator == null) {
            throw new IllegalArgumentException("No ContentGenerator registered for platform: " + platform);
        }
        return generator;
    }

    public boolean isRegistered(MarketingContent.Platform platform) {
        return registry.containsKey(platform);
    }
}
