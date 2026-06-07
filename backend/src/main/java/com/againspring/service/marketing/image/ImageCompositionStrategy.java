package com.againspring.service.marketing.image;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.service.marketing.content.GenerationOutput;

import java.io.IOException;
import java.util.List;

/**
 * Renders platform-specific image assets from the generation output.
 * Each implementation handles one Platform.
 */
public interface ImageCompositionStrategy {

    MarketingContent.Platform supports();

    /**
     * Compose images for the given content.
     *
     * @param output       structured LLM output from the corresponding ContentGenerator
     * @param relationType relation type string (e.g. "couple", "friend") for metaphor selection
     * @param contentId    ID of the marketing_contents row (used for filename prefix)
     * @param imageDir     absolute path to the directory where PNGs are saved
     * @return list of rendered image metadata (empty if all renders fail non-fatally)
     */
    List<RenderedImage> compose(
            GenerationOutput output,
            String relationType,
            Long contentId,
            String imageDir
    ) throws IOException;
}
