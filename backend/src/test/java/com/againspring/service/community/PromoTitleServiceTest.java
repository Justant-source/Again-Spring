package com.againspring.service.community;

import com.againspring.domain.community.Post;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromoTitleServiceTest {

    @Test
    void truncate_cutsToMax() {
        assertEquals("一二三四五六七八九十1234567890",
                PromoTitleService.truncate("一二三四五六七八九十1234567890extra", 20));
        assertEquals("짧음", PromoTitleService.truncate("짧음", 20));
    }

    @Test
    void resolveOrFallback_prefersPromo() {
        Post post = Post.builder()
                .id("p1")
                .authorId("a1")
                .title("원제목이 아주 긴 제목입니다 여기까지")
                .promoTitle("연락 한 통이 뭐길래")
                .build();
        assertEquals("연락 한 통이 뭐길래", PromoTitleService.resolveOrFallback(post));
    }

    @Test
    void resolveOrFallback_truncatesTitleWhenMissingPromo() {
        Post post = Post.builder()
                .id("p1")
                .authorId("a1")
                .title("원제목이 아주 긴 제목입니다 여기까지더")
                .build();
        String got = PromoTitleService.resolveOrFallback(post);
        assertEquals(20, got.length());
        assertEquals("원제목이 아주 긴 제목입니다 여기까지더".substring(0, 20), got);
    }
}
