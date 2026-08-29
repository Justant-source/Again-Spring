package com.againspring.service.community;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SibomPlanGuardTest {

    @Test
    void dropsUnknownImageId() {
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("intro", "not-a-real-id", "캡션", 0, "large", "hold"),
                item("peak", "stunned", "말문이 막혔다", 2, "large", "hold")
        ), SibomPlanGuard.Channel.REELS);

        // After soft-fill topup, should be >= MIN_REELS (4)
        assertThat(out.size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_REELS);
        assertThat(out.stream().map(SibomPlanItem::imageId)).contains("stunned");
    }

    @Test
    void captionOverMaxChars_swapsSiblingOrClears() {
        // money-trouble maxChars=10; sibling money-trouble (self) → empty caption
        List<SibomPlanItem> self = SibomPlanGuard.guard(List.of(
                item("punch", "money-trouble", "이것은열일곱자가넘는매우긴캡션입니다", 1, "small", "punch")
        ), SibomPlanGuard.Channel.REELS);
        // After soft-fill topup, should be >= MIN_REELS
        assertThat(self.size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_REELS);
        assertThat(self.stream().filter(i -> i.imageId().equals("money-trouble")).findFirst().get().caption()).isEmpty();

        // two-argue maxChars=10, sibling_bottom=two-cold-backs (also 10)
        List<SibomPlanItem> swapped = SibomPlanGuard.guard(List.of(
                item("punch", "two-argue", "이것은열일곱자가넘는매우긴캡션입니다", 1, "small", "punch")
        ), SibomPlanGuard.Channel.REELS);
        assertThat(swapped.size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_REELS);
        assertThat(swapped.stream().map(SibomPlanItem::imageId)).contains("two-cold-backs");
        assertThat(swapped.stream().filter(i -> i.imageId().equals("two-cold-backs")).findFirst().get().caption()).isEmpty();
    }

    @Test
    void softFillHeroPresentation_demotesToPunch() {
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("soft_fill", "drained", "이제 지쳤다", 1, "large", "hold")
        ), SibomPlanGuard.Channel.REELS);

        // After soft-fill topup, should be >= MIN_REELS
        assertThat(out.size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_REELS);
        // drained (original hero attempt) should be demoted to punch
        assertThat(out.stream().filter(i -> i.imageId().equals("drained")).findFirst().get().role()).isEqualTo("punch");
        assertThat(out.stream().allMatch(i -> i.size().equalsIgnoreCase("small") || i.size().equalsIgnoreCase("large"))).isTrue();
    }

    @Test
    void softFillNonPool_demotesToPunch() {
        // late-regret: people()==1 but not in SOFT_FILL_POOL — must still demote.
        // (money-trouble used to be the example here; it joined the pool in the
        // 2026-08-22 7→14 expansion, so it no longer demonstrates this branch.)
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("soft_fill", "late-regret", "그때 생각", 1, "small", "punch")
        ), SibomPlanGuard.Channel.SHORTS);

        assertThat(out.get(0).role()).isEqualTo("punch");
    }

    @Test
    void dedupesImageIdAndSwapGroup() {
        // burst-crying and breakup share swap_group=grief
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("peak", "burst-crying", "울었다", 3, "large", "hold"),
                item("punch", "burst-crying", "또", 4, "small", "punch"),
                item("punch", "breakup", "이별", 5, "small", "punch"),
                item("soft_fill", "drained", "지쳤다", 6, "small", "punch")
        ), SibomPlanGuard.Channel.SHORTS);

        // After dedupe + soft-fill topup, breakup should be removed (same swap_group as burst-crying)
        // drained should be preserved, and other soft-fill items may be added
        assertThat(out).extracting(SibomPlanItem::imageId).contains("burst-crying", "drained");
        // breakup should NOT be present (same swap_group as burst-crying)
        assertThat(out).extracting(SibomPlanItem::imageId).doesNotContain("breakup");
    }

    @Test
    void trimsOverBudget_preferDroppingPunchSoftFill() {
        List<SibomPlanItem> raw = new ArrayList<>();
        raw.add(item("intro", "side-glance", "눈치", 0, "large", "hold"));
        raw.add(item("peak", "stunned", "멍", 3, "large", "hold"));
        raw.add(item("punch", "waiting-reply", "읽씹", 4, "small", "punch"));
        raw.add(item("soft_fill", "drained", "지침", 5, "small", "punch"));
        raw.add(item("soft_fill", "curled-up", "웅크림", 6, "small", "punch"));
        raw.add(item("soft_fill", "indignant", "억울", 7, "small", "punch"));
        // Reels max 5 → drop last soft_fill
        List<SibomPlanItem> out = SibomPlanGuard.guard(raw, SibomPlanGuard.Channel.REELS);
        assertThat(out.size()).isLessThanOrEqualTo(SibomPlanGuard.MAX_REELS);
        assertThat(out).extracting(SibomPlanItem::role).contains("intro", "peak");
    }

    @Test
    void peakTooEarly_demotesFirstPeak() {
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("intro", "side-glance", "눈치", 0, "large", "hold"),
                item("peak", "stunned", "충격", 0, "large", "hold"), // too early vs maxBeat=8
                item("punch", "drained", "지침", 8, "small", "punch")
        ), SibomPlanGuard.Channel.REELS);

        assertThat(out.stream().filter(i -> "peak".equals(i.role())).count()).isZero();
        assertThat(out.stream().anyMatch(i -> "punch".equals(i.role()) && "stunned".equals(i.imageId())))
                .isTrue();
    }

    @Test
    void secondPeak_nonResolution_demotes() {
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("peak", "stunned", "충격", 3, "large", "hold"), // trigger arc
                item("peak", "indignant", "억울", 8, "large", "hold") // not resolution
        ), SibomPlanGuard.Channel.SHORTS);

        long peaks = out.stream().filter(i -> "peak".equals(i.role())).count();
        assertThat(peaks).isEqualTo(1);
    }

    @Test
    void secondPeak_resolutionLate_kept() {
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("peak", "stunned", "충격", 3, "large", "hold"),
                item("peak", "relieved", "한숨", 8, "large", "hold") // resolution + late
        ), SibomPlanGuard.Channel.SHORTS);

        assertThat(out.stream().filter(i -> "peak".equals(i.role())).count()).isEqualTo(2);
    }

    @Test
    void emptyPlan_noMetaphorFallback() {
        assertThat(SibomPlanGuard.guard(List.of(), SibomPlanGuard.Channel.REELS)).isEmpty();
        assertThat(SibomPlanGuard.guard(null, SibomPlanGuard.Channel.REELS)).isEmpty();
    }

    @Test
    void catalogLoaded() {
        assertThat(SibomCatalog.size()).isGreaterThanOrEqualTo(30);
        assertThat(SibomCatalog.isKnown("drained")).isTrue();
        assertThat(SibomCatalog.isKnown("metaphor-fake")).isFalse();
        assertThat(SibomCatalog.entries())
                .allSatisfy(entry -> {
                    assertThat(entry.maxChars()).isEqualTo(SibomPlanGuard.CAPTION_MAX_CHARS);
                    assertThat(entry.caption()).hasSizeLessThanOrEqualTo(SibomPlanGuard.CAPTION_MAX_CHARS);
                });
    }

    @Test
    void softFillTopUp_dedupeThreeItems_becomesFourWithSoftFill() {
        // Simulate dedupe result: 3 items (burst-crying and breakup deduplicated)
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("peak", "burst-crying", "울었다", 3, "large", "hold"),
                item("punch", "burst-crying", "또 울었다", 4, "small", "punch"),
                item("punch", "breakup", "이별", 5, "small", "punch")
        ), SibomPlanGuard.Channel.SHORTS);

        // After dedupe + soft-fill topup: should become 4
        assertThat(out).hasSizeGreaterThanOrEqualTo(4);
        assertThat(out.stream().filter(i -> "soft_fill".equals(i.role())).count()).isGreaterThan(0);
    }

    @Test
    void softFillTopUp_excludesUsedImageIdAndSwapGroup() {
        // drained + curled-up are in SOFT_FILL_POOL
        // burst-crying and drained share no group; curled-up and indignant don't share group
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("peak", "burst-crying", "울었다", 2, "large", "hold"),
                item("punch", "drained", "지침", 3, "small", "punch")
                // 2 items, need 4 for SHORTS
        ), SibomPlanGuard.Channel.SHORTS);

        // topUpWithSoftFill should fill with curled-up, stunned, etc., but NOT drained again
        assertThat(out).hasSizeGreaterThanOrEqualTo(4);
        assertThat(out.stream().filter(i -> i.imageId().equals("drained")).count()).isEqualTo(1);
    }

    @Test
    void softFillTopUp_poolExhausted_returnsAsIs() {
        // Force using all pool items, then more needed
        List<SibomPlanItem> manyPoolItems = new ArrayList<>();
        manyPoolItems.add(item("punch", "drained", "지침", 0, "small", "punch"));
        manyPoolItems.add(item("punch", "curled-up", "웅크림", 1, "small", "punch"));
        manyPoolItems.add(item("punch", "stunned", "멍", 2, "small", "punch"));
        manyPoolItems.add(item("punch", "swallow-words", "말 삼킴", 3, "small", "punch"));
        manyPoolItems.add(item("punch", "indignant", "억울", 4, "small", "punch"));
        manyPoolItems.add(item("punch", "side-glance", "눈치", 5, "small", "punch"));
        manyPoolItems.add(item("punch", "relieved", "한숨", 6, "small", "punch"));

        List<SibomPlanItem> out = SibomPlanGuard.guard(manyPoolItems, SibomPlanGuard.Channel.SHORTS);

        // After guard (trim + normalize), should not throw, just return what it can
        assertThat(out).isNotEmpty();
        assertThat(out.size()).isLessThanOrEqualTo(SibomPlanGuard.MAX_SHORTS);
    }

    @Test
    void softFillTopUp_itemsNeverPromotedToIntroOrPeak() {
        List<SibomPlanItem> twoItems = List.of(
                item("peak", "burst-crying", "울었다", 2, "large", "hold"),
                item("punch", "money-trouble", "돈 문제", 3, "small", "punch")
        );

        List<SibomPlanItem> out = SibomPlanGuard.guard(twoItems, SibomPlanGuard.Channel.SHORTS);

        // All soft_fill items should have role=soft_fill (before normalizeSizeDwell),
        // and normalizeSizeDwell ensures soft_fill → small+punch
        assertThat(out.stream()
                .filter(i -> "soft_fill".equals(i.role()))
                .allMatch(i -> i.size().equalsIgnoreCase("small") && i.dwell().equalsIgnoreCase("punch")))
                .isTrue();
    }

    @Test
    void minShortsEqualsFour() {
        assertThat(SibomPlanGuard.MIN_SHORTS).isEqualTo(4);
    }

    @Test
    void fourShortsPlan_passesQualityGate() {
        List<SibomPlanItem> fourItems = List.of(
                item("intro", "side-glance", "눈치", 0, "large", "hold"),
                item("peak", "stunned", "멍", 2, "large", "hold"),
                item("punch", "drained", "지침", 4, "small", "punch"),
                item("punch", "curled-up", "웅크림", 5, "small", "punch")
        );

        List<SibomPlanItem> guarded = SibomPlanGuard.guard(fourItems, SibomPlanGuard.Channel.SHORTS);
        assertThat(guarded.size()).isGreaterThanOrEqualTo(SibomPlanGuard.MIN_SHORTS);
    }

    // --- §5.1 caption content gate (2026-08-29, job 01M13K1KH1SYEMYSH5PCFFJP9N incident) ---
    // marketing_generation_trace id=11 showed the LLM literally lifted body words into
    // sibom_plan captions ("상의없이", "오백만원" — from title "아내가 상의없이 오백만원
    // 빌려준 걸 알았다" / body "...상의도 없이 그냥 빌려줬다는 게...오백만원이 빠져나간...").
    // sibom_plan_llm == sibom_plan_final in the trace, proving the schema guard let it through
    // unchanged (it only checked caption length, never content).

    private static final String INCIDENT_TITLE = "아내가 상의없이 오백만원 빌려준 걸 알았다";
    private static final String INCIDENT_BODY = String.join("\n",
            "어제 저녁에 밥 차려놓고 아내 기다렸어",
            "계모임 저녁 약속 있다고만 하고 나갔거든",
            "아홉시 넘어서 카톡으로 늦는다고만 왔어",
            "자정 다 돼서야 들어오더라",
            "근데 오늘 통장 정리하다가 오백만원이 빠져나간 걸 봤어",
            "왜 나갔냐고 물으니 계모임 언니가 급하다고 해서 빌려줬대",
            "언제 갚는지 나랑 상의도 없이 그냥 빌려줬다는 게",
            "결혼하고 처음으로 진짜 낯설게 느껴졌어");

    @Test
    void buildLeakIndex_catchesIncidentCaptions_butNotLegitimateEmotionLabel() {
        String leakIndex = SibomPlanGuard.buildLeakIndex(INCIDENT_TITLE, INCIDENT_BODY);

        // Actual bad captions from the incident trace — literal body/title chunks.
        assertThat(SibomPlanGuard.isBodyLeak("상의없이", leakIndex)).isTrue();
        assertThat(SibomPlanGuard.isBodyLeak("오백만원", leakIndex)).isTrue();

        // "낯섦" (nominalized) never appears verbatim in the body ("낯설게", adjective form) —
        // must NOT be flagged, since this is exactly the kind of good label the user confirmed
        // as normal ("정상: 낯섦", "말못함").
        assertThat(SibomPlanGuard.isBodyLeak("낯섦", leakIndex)).isFalse();
        assertThat(SibomPlanGuard.isBodyLeak("말못함", leakIndex)).isFalse();
    }

    @Test
    void guardWithLog_bodyLeakCaption_replacedWithCatalogDefault() {
        String leakIndex = SibomPlanGuard.buildLeakIndex(INCIDENT_TITLE, INCIDENT_BODY);

        SibomPlanGuard.GuardResult result = SibomPlanGuard.guardWithLog(List.of(
                item("intro", "decision-announced", "상의없이", 0, "large", "hold"),
                item("peak", "money-trouble", "오백만원", 1, "large", "hold"),
                item("soft_fill", "stunned", "낯섦", 2, "small", "punch")
        ), SibomPlanGuard.Channel.SHORTS, leakIndex);

        SibomPlanItem intro = result.items().stream()
                .filter(i -> i.imageId().equals("decision-announced")).findFirst().orElseThrow();
        SibomPlanItem peak = result.items().stream()
                .filter(i -> i.imageId().equals("money-trouble")).findFirst().orElseThrow();
        SibomPlanItem softFill = result.items().stream()
                .filter(i -> i.imageId().equals("stunned")).findFirst().orElseThrow();

        // Leaked captions replaced with the catalog's own vetted default for that image.
        assertThat(intro.caption()).isEqualTo(SibomCatalog.get("decision-announced").get().caption());
        assertThat(peak.caption()).isEqualTo(SibomCatalog.get("money-trouble").get().caption());
        assertThat(intro.caption()).doesNotContain("상의없이");
        assertThat(peak.caption()).doesNotContain("오백만원");

        // Legitimate emotion label untouched.
        assertThat(softFill.caption()).isEqualTo("낯섦");

        assertThat(result.log()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("caption_replaced");
            assertThat(entry.reason()).contains("body_leak");
        });
    }

    @Test
    void guardWithLog_amountCaption_replacedEvenWithoutBodyMatch() {
        // A caption made of digits (e.g. LLM writes "500만원" instead of "오백만원") is never
        // a valid emotion label regardless of whether it literally appears in the body.
        SibomPlanGuard.GuardResult result = SibomPlanGuard.guardWithLog(List.of(
                item("peak", "money-trouble", "500만원", 1, "large", "hold")
        ), SibomPlanGuard.Channel.SHORTS, null);

        SibomPlanItem peak = result.items().stream()
                .filter(i -> i.imageId().equals("money-trouble")).findFirst().orElseThrow();
        assertThat(peak.caption()).isEqualTo(SibomCatalog.get("money-trouble").get().caption());
        assertThat(result.log()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("caption_replaced");
            assertThat(entry.reason()).contains("amount_or_number");
        });
    }

    @Test
    void guardWithLog_forbiddenWordCaption_replaced() {
        SibomPlanGuard.GuardResult result = SibomPlanGuard.guardWithLog(List.of(
                item("punch", "stunned", "가해자", 1, "small", "punch")
        ), SibomPlanGuard.Channel.SHORTS, null);

        SibomPlanItem punch = result.items().stream()
                .filter(i -> i.imageId().equals("stunned")).findFirst().orElseThrow();
        assertThat(punch.caption()).isEqualTo(SibomCatalog.get("stunned").get().caption());
        assertThat(punch.caption()).doesNotContain("가해자");
        assertThat(result.log()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("caption_replaced");
            assertThat(entry.reason()).contains("forbidden_word");
        });
    }

    @Test
    void guardWithLog_noLeakIndex_skipsBodyLeakCheckButStillCatchesAmountAndForbidden() {
        // Legacy 2-arg overload (no post text on hand) still runs the amount/forbidden checks,
        // just not the body-leak check.
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("intro", "decision-announced", "상의없이", 0, "large", "hold")
        ), SibomPlanGuard.Channel.SHORTS);

        // Without a leak index, "상의없이" isn't flagged as a body leak (nothing to compare
        // against), so it passes through as normal LLM output would have before this fix.
        assertThat(out.stream().filter(i -> i.imageId().equals("decision-announced")).findFirst().get().caption())
                .isEqualTo("상의없이");
    }

    private static SibomPlanItem item(
            String role, String id, String caption, int beat, String size, String dwell) {
        return new SibomPlanItem(role, id, caption, beat, size, dwell);
    }
}
