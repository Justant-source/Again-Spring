package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.againspring.domain.marketing.MarketingContent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes content generation requests to the appropriate ContentGenerator via registry.
 * Adding a new platform requires only a new ContentGenerator bean + YAML entry.
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PlatformContentRouter {

    private final ContentGeneratorRegistry registry;
    private final PlatformDescriptorLoader descriptorLoader;

    public GenerationOutput generate(MarketingContent.Platform platform, String simulationSummary,
            String relationType) throws Exception {
        PlatformDescriptor descriptor = descriptorLoader.get(platform);
        GenerationContext ctx = GenerationContext.of(simulationSummary, relationType, descriptor);
        return registry.resolve(platform).generate(ctx);
    }

    public GenerationOutput generateWithTemplate(MarketingContent.Platform platform, String simulationSummary,
            String relationType, String templateBody) throws Exception {
        PlatformDescriptor descriptor = descriptorLoader.get(platform);
        GenerationContext ctx = new GenerationContext(simulationSummary, relationType, descriptor, templateBody, null);
        return registry.resolve(platform).generate(ctx);
    }
}
