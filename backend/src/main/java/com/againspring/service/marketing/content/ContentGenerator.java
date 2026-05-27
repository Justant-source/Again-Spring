package com.againspring.service.marketing.content;

import com.againspring.domain.marketing.MarketingContent;

/**
 * Platform-specific content generator interface.
 * Each implementation handles a single Platform and must be registered as a Spring bean.
 */
public interface ContentGenerator {

    /**
     * Generate marketing copy for the target platform.
     *
     * @param ctx generation context (summary, relation type, descriptor, optional template)
     * @return structured output (bodyText + hashtags + structuredPayload for image composition)
     * @throws Exception if LLM invocation fails
     */
    GenerationOutput generate(GenerationContext ctx) throws Exception;

    /**
     * Returns the Platform this generator handles. Used by ContentGeneratorRegistry.
     */
    MarketingContent.Platform supports();
}
