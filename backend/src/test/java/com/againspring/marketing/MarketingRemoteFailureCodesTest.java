package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingRemoteFailureCodesTest {

    @Test
    void liftsNestedSibomCodeWhenWaggleReportsRenderUnknown() {
        Map<String, Object> diagnostics = Map.of(
            "youtube_shorts", Map.of(
                "platform", "youtube_shorts",
                "sibom_plan_count", 4,
                "sibom_applied_count", 4,
                "sibom_required_count", 5,
                "failure_code", "SIBOM_SCENES_TOO_SHORT"
            )
        );

        assertThat(MarketingRemoteFailureCodes.resolve("RENDER_UNKNOWN", diagnostics))
            .isEqualTo("SIBOM_SCENES_TOO_SHORT");
        assertThat(MarketingRemoteFailureCodes.looksLikeRawDump(
            "{'ok': True, 'jobId': 10027231, 'status': 'FAILED', 'sibom_plan_count'"))
            .isTrue();
    }

    @Test
    void keepsExplicitNonGenericCode() {
        assertThat(MarketingRemoteFailureCodes.resolve(
            "INFRA_DB_CONFLICT",
            Map.of("failure_code", "SIBOM_SCENES_TOO_SHORT")
        )).isEqualTo("INFRA_DB_CONFLICT");
    }
}
