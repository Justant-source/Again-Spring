package com.againspring.service.marketing.image;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.service.marketing.content.GenerationOutput;

import java.io.IOException;
import java.util.List;

/**
 * Renders platform-specific image assets from the generation output.
 * Each implementation handles one Platform.
 * NOTE: Report class removed due to deletion of mediation code.
 */
public interface ImageCompositionStrategy {

    MarketingContent.Platform supports();

    /**
     * Compose images for the given content.
     *
     * @param output    structured LLM output from the corresponding ContentGenerator
     * @param sim       simulation (for session ID and persona metadata)
     * @param report    null (Report class was removed)
     * @param contentId ID of the marketing_contents row (used for filename prefix)
     * @param imageDir  absolute path to the directory where PNGs are saved
     * @return list of rendered image metadata (empty if all renders fail non-fatally)
     */
    List<RenderedImage> compose(
            GenerationOutput output,
            MarketingSimulation sim,
            Object report,  // Stub: was Report report
            Long contentId,
            String imageDir
    ) throws IOException;
}
