package com.againspring.service.community;

import com.againspring.domain.community.Post;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void normalizeAgainstTitle_repacksOrphanHeavyBreaks() {
        String title = "왜 만나자는 말 안 하냐고";
        String bad = "왜\n만나자는\n말\n안\n하냐고";
        String got = PromoTitleService.normalizeAgainstTitle(bad, title);
        assertEquals(PromoTitleService.collapseWs(title),
                PromoTitleService.collapseWs(got.replace("\n", "")));
        for (String line : got.split("\n")) {
            assertTrue(line.length() >= PromoTitleService.MIN_LINE_LEN || got.split("\n").length == 1, line);
        }
    }

    @Test
    void normalizeAgainstTitle_keepsValidBreaks() {
        String title = "도와줬더니 모든 걸 저한테 의존하는 동료";
        String promo = "도와줬더니\n모든 걸\n저한테\n의존하는 동료";
        String got = PromoTitleService.normalizeAgainstTitle(promo, title);
        assertTrue(got.contains("\n"));
        assertEquals(PromoTitleService.collapseWs(title),
                PromoTitleService.collapseWs(got.replace("\n", "")));
    }

    @Test
    void normalizeAgainstTitle_rejectsRewrite() {
        String title = "원제입니다 그대로";
        String promo = "완전히\n다른\n훅";
        String got = PromoTitleService.normalizeAgainstTitle(promo, title);
        assertEquals(PromoTitleService.collapseWs(title),
                PromoTitleService.collapseWs(got.replace("\n", "")));
    }

    @Test
    void resolveOrFallback_prefersPromoWithNewlines() {
        Post post = Post.builder()
                .id("p1")
                .authorId("a1")
                .title("원제목이 아주 긴 제목입니다")
                .promoTitle("원제목이\n아주 긴\n제목입니다")
                .build();
        assertEquals("원제목이\n아주 긴\n제목입니다", PromoTitleService.resolveOrFallback(post));
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
    }
}
