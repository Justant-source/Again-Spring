package com.againspring.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarketingRenderProfilesTest {

    @Test
    void allowsExistingAndHookProfiles() {
        for (String p : new String[] {"marketing_fast", "marketing_v2",
            "marketing_hook_a", "marketing_hook_b", "marketing_hook_c"}) {
            assertThat(MarketingRenderProfiles.isAllowed(p)).as(p).isTrue();
        }
    }

    @Test
    void rejectsUnknownAndNull() {
        assertThat(MarketingRenderProfiles.isAllowed("marketing_hook_d")).isFalse();
        assertThat(MarketingRenderProfiles.isAllowed("")).isFalse();
        assertThat(MarketingRenderProfiles.isAllowed(null)).isFalse();
    }

    @Test
    void onlyHookBAndCAreExperimental() {
        assertThat(MarketingRenderProfiles.isExperimental("marketing_hook_b")).isTrue();
        assertThat(MarketingRenderProfiles.isExperimental("marketing_hook_c")).isTrue();
        assertThat(MarketingRenderProfiles.isExperimental("marketing_hook_a")).isFalse();
        assertThat(MarketingRenderProfiles.isExperimental("marketing_v2")).isFalse();
        assertThat(MarketingRenderProfiles.isExperimental("marketing_fast")).isFalse();
    }
}
