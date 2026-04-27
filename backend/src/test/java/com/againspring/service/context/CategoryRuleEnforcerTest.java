package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import org.junit.jupiter.api.Test;

class CategoryRuleEnforcerTest {

    private final CategoryRuleEnforcer enforcer = new CategoryRuleEnforcer();

    private Session.IssueFact fact(String text) {
        Session.IssueFact f = new Session.IssueFact();
        f.text = text;
        return f;
    }

    // in_law 카테고리

    @Test
    void inLaw_rejectsThirdPartyJudgment_withDiscriminationWord() {
        assertFalse(enforcer.isFactAllowed(fact("시어머니가 차별했다"), "in_law"));
    }

    @Test
    void inLaw_rejectsThirdPartyJudgment_withFaultWord() {
        assertFalse(enforcer.isFactAllowed(fact("시어머니 잘못이다"), "in_law"));
    }

    @Test
    void inLaw_allowsNeutralFact() {
        assertTrue(enforcer.isFactAllowed(fact("시어머니 댁에 방문했다"), "in_law"));
    }

    // lingered 카테고리

    @Test
    void lingered_rejectsSingleEventFact_withYesterday() {
        assertFalse(enforcer.isFactAllowed(fact("어제 무슨 일이 있었다"), "lingered"));
    }

    @Test
    void lingered_rejectsSingleEventFact_withThatDay() {
        assertFalse(enforcer.isFactAllowed(fact("그날 그 일이 있었다"), "lingered"));
    }

    @Test
    void lingered_allowsPatternFact() {
        assertTrue(enforcer.isFactAllowed(fact("오랫동안 비슷한 일이 반복됐다"), "lingered"));
    }

    // generation 카테고리

    @Test
    void generation_rejectsValueHierarchyWord() {
        assertFalse(enforcer.isFactAllowed(fact("구식 가치관이다"), "generation"));
    }

    @Test
    void generation_allowsNeutralBehaviorFact() {
        assertTrue(enforcer.isFactAllowed(fact("명절 의례 방식이 서로 다르다"), "generation"));
    }

    // 일반 카테고리

    @Test
    void couple_allowsAnyFactText() {
        assertTrue(enforcer.isFactAllowed(fact("어제 말다툼이 있었다"), "couple"));
    }

    @Test
    void nullFact_returnsFalse() {
        assertFalse(enforcer.isFactAllowed(null, "in_law"));
    }

    @Test
    void nullText_returnsFalse() {
        Session.IssueFact f = new Session.IssueFact();
        assertFalse(enforcer.isFactAllowed(f, "in_law"));
    }

    // Intent 검사

    @Test
    void lingered_disallowsSeekFact() {
        assertFalse(enforcer.isIntentAllowed(Session.Intent.SEEK_FACT, "lingered"));
    }

    @Test
    void lingered_allowsSeekNeed() {
        assertTrue(enforcer.isIntentAllowed(Session.Intent.SEEK_NEED, "lingered"));
    }

    @Test
    void couple_allowsAllIntents() {
        for (Session.Intent i : Session.Intent.values()) {
            assertTrue(enforcer.isIntentAllowed(i, "couple"),
                "Expected intent " + i + " to be allowed for couple");
        }
    }
}
