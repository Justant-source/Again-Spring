package com.againspring.service.community;

import com.againspring.domain.community.Post;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromoTitleServiceTest {

    @Test
    void wrapSemantic_shortTitleUnchanged() {
        assertEquals("짧음", PromoTitleService.wrapSemantic("짧음"));
    }

    @Test
    void wrapSemantic_linesAtMostTen_noOrphanSyllables() {
        String title = "어장관리하는 남자랑 연락하면서 왜 만나자는 말 안 하냐고 글 올린 사람 봤는데";
        String wrapped = PromoTitleService.wrapSemantic(title);
        assertEquals(PromoTitleService.collapseWs(title),
                PromoTitleService.collapseWs(wrapped.replace("\n", "")));
        String[] lines = wrapped.split("\n");
        assertTrue(lines.length <= 8, "expected packed lines, got " + lines.length + ": " + wrapped);
        for (String line : lines) {
            assertTrue(line.length() <= PromoTitleService.MAX_LINE_LEN, line);
            assertTrue(line.length() >= PromoTitleService.MIN_LINE_LEN
                    || lines.length == 1, "orphan: " + line);
        }
        assertFalse(wrapped.contains("\n왜\n") || wrapped.endsWith("\n왜") || wrapped.startsWith("왜\n"));
    }

    @Test
    void normalizeHook_repacksOrphanHeavyBreaks() {
        String bad = "왜\n만\n나\n자";
        String got = PromoTitleService.normalizeHook(bad);
        for (String line : got.split("\n")) {
            assertTrue(line.length() >= PromoTitleService.MIN_LINE_LEN || got.split("\n").length == 1, line);
        }
    }

    @Test
    void normalizeHook_keepsValidBreaks_withoutTitleEquality() {
        String promo = "그 말\n진짜야?\n충격";
        String got = PromoTitleService.normalizeHook(promo);
        assertTrue(got.contains("\n"));
        assertEquals("그 말\n진짜야?\n충격", got);
    }

    @Test
    void normalizeHook_allowsRewriteDifferentFromTitle() {
        // master hook may diverge from original title — must not be rewritten back
        String promo = "완전히\n다른\n훅이다";
        String got = PromoTitleService.normalizeHook(promo);
        assertEquals(PromoTitleService.collapseWs("완전히다른훅이다"),
                PromoTitleService.collapseWs(got.replace("\n", "")));
    }

    @Test
    void normalizeAgainstTitle_blankFallsBackToWrap() {
        String title = "원제입니다 그대로";
        String got = PromoTitleService.normalizeAgainstTitle(null, title);
        assertEquals(PromoTitleService.collapseWs(title),
                PromoTitleService.collapseWs(got.replace("\n", "")));
    }

    @Test
    void validateEmotion_acceptsAllowedValues() {
        for (String e : PromoTitleService.HOOK_EMOTIONS) {
            assertEquals(e, PromoTitleService.validateEmotion(e));
            assertEquals(e, PromoTitleService.validateEmotion(e.toUpperCase()));
            assertEquals(e, PromoTitleService.validateEmotion("  " + e + "  "));
        }
    }

    @Test
    void validateEmotion_rejectsUnknownAndBlank() {
        assertNull(PromoTitleService.validateEmotion(null));
        assertNull(PromoTitleService.validateEmotion(""));
        assertNull(PromoTitleService.validateEmotion("   "));
        assertNull(PromoTitleService.validateEmotion("joy"));
        assertNull(PromoTitleService.validateEmotion("shocking"));
        assertNull(PromoTitleService.validateEmotion("판정"));
    }

    @Test
    void resolveOrFallback_prefersPromoWithNewlines() {
        Post post = Post.builder()
                .id("p1")
                .authorId("a1")
                .title("원제목이 아주 긴 제목입니다")
                .promoTitle("원제목이\n아주 긴\n제목입니다")
                .hookEmotion("tension")
                .build();
        assertEquals("원제목이\n아주 긴\n제목입니다", PromoTitleService.resolveOrFallback(post));
        assertEquals("tension", post.getHookEmotion());
    }

    @Test
    void resolveOrFallback_wrapsWhenMissingPromo() {
        String title = "가나다라마바사아자차카타파하가나다라마";
        Post post = Post.builder()
                .id("p1")
                .authorId("a1")
                .title(title)
                .build();
        String got = PromoTitleService.resolveOrFallback(post);
        assertTrue(got.contains("\n"));
        assertEquals(PromoTitleService.collapseWs(title),
                PromoTitleService.collapseWs(got.replace("\n", "")));
        assertNull(post.getHookEmotion());
    }
}
