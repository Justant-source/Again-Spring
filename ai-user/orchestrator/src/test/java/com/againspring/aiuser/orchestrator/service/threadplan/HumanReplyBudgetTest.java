package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HumanReplyBudgetTest {

    @Test
    void enforcesPostPersonaAndDistinctCaps() {
        HumanReplyBudget budget = new HumanReplyBudget(15, 5, 3);

        assertThat(budget.tryAccept("a")).isTrue();
        assertThat(budget.tryAccept("b")).isTrue();
        assertThat(budget.tryAccept("c")).isTrue();
        assertThat(budget.tryAccept("d")).isFalse(); // distinct max 3

        for (int i = 0; i < 4; i++) assertThat(budget.tryAccept("a")).isTrue();
        assertThat(budget.tryAccept("a")).isFalse(); // persona max 5

        // fill remaining post budget with b/c (4 each → post 7+4+4=15)
        for (int i = 0; i < 4; i++) assertThat(budget.tryAccept("b")).isTrue();
        for (int i = 0; i < 4; i++) assertThat(budget.tryAccept("c")).isTrue();
        assertThat(budget.postCount()).isEqualTo(15);
        assertThat(budget.tryAccept("b")).isFalse(); // post max
    }

    @Test
    void seedCountsTowardCaps() {
        HumanReplyBudget budget = new HumanReplyBudget(15, 5, 3);
        budget.seed("a");
        budget.seed("a");
        budget.seed("b");
        assertThat(budget.postCount()).isEqualTo(3);
        assertThat(budget.personaCount("a")).isEqualTo(2);
        assertThat(budget.distinctCount()).isEqualTo(2);
        assertThat(budget.canAccept("c")).isTrue();
        assertThat(budget.canAccept("a")).isTrue();
    }
}
