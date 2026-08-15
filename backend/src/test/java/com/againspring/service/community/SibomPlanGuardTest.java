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
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("soft_fill", "money-trouble", "돈 문제", 1, "small", "punch")
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

    private static SibomPlanItem item(
            String role, String id, String caption, int beat, String size, String dwell) {
        return new SibomPlanItem(role, id, caption, beat, size, dwell);
    }
}
