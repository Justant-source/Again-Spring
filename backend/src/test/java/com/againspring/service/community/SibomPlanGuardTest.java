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

        assertThat(out).hasSize(1);
        assertThat(out.get(0).imageId()).isEqualTo("stunned");
        assertThat(out.get(0).role()).isEqualTo("peak");
    }

    @Test
    void captionOverMaxChars_swapsSiblingOrClears() {
        // money-trouble maxChars=10; sibling money-trouble (self) → empty caption
        List<SibomPlanItem> self = SibomPlanGuard.guard(List.of(
                item("punch", "money-trouble", "이것은열일곱자가넘는매우긴캡션입니다", 1, "small", "punch")
        ), SibomPlanGuard.Channel.REELS);
        assertThat(self).hasSize(1);
        assertThat(self.get(0).caption()).isEmpty();

        // two-argue maxChars=10, sibling_bottom=two-cold-backs (also 10)
        List<SibomPlanItem> swapped = SibomPlanGuard.guard(List.of(
                item("punch", "two-argue", "이것은열일곱자가넘는매우긴캡션입니다", 1, "small", "punch")
        ), SibomPlanGuard.Channel.REELS);
        assertThat(swapped).hasSize(1);
        assertThat(swapped.get(0).imageId()).isEqualTo("two-cold-backs");
        assertThat(swapped.get(0).caption()).isEmpty(); // still over sibling maxChars
    }

    @Test
    void softFillHeroPresentation_demotesToPunch() {
        List<SibomPlanItem> out = SibomPlanGuard.guard(List.of(
                item("soft_fill", "drained", "이제 지쳤다", 1, "large", "hold")
        ), SibomPlanGuard.Channel.REELS);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).role()).isEqualTo("punch");
        assertThat(out.get(0).size()).isEqualTo("small");
        assertThat(out.get(0).dwell()).isEqualTo("punch");
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

        assertThat(out).extracting(SibomPlanItem::imageId)
                .containsExactly("burst-crying", "drained");
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

    private static SibomPlanItem item(
            String role, String id, String caption, int beat, String size, String dwell) {
        return new SibomPlanItem(role, id, caption, beat, size, dwell);
    }
}
