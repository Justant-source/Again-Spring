package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import com.againspring.service.category.CategoryCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * V47~: 중·소분류 제거 후 CategoryContextFragment 테스트.
 * majorId + customText만 잔존.
 */
class CategoryContextFragmentTest {

    private CategoryContextFragment fragment;

    @BeforeEach
    void setUp() {
        CategoryCatalog catalog = new CategoryCatalog(
                new DefaultResourceLoader(),
                "classpath:test-categories.yml");
        catalog.load();
        fragment = new CategoryContextFragment(catalog);
    }

    @Test
    void render_returnsEmpty_whenSessionNull() {
        assertEquals("", fragment.render(null));
    }

    @Test
    void render_returnsEmpty_whenCategoryNull() {
        Session s = new Session();
        assertEquals("", fragment.render(s));
    }

    @Test
    void render_returnsEmpty_whenMajorIdNull() {
        Session s = new Session();
        s.setCategory(new Session.Category());
        assertEquals("", fragment.render(s));
    }

    @Test
    void render_returnsEmpty_whenMajorNotInCatalog() {
        Session s = sessionWithMajor("nonexistent", null);
        assertEquals("", fragment.render(s));
    }

    @Test
    void render_includesMajorLabel() {
        Session s = sessionWithMajor("couple", null);
        String result = fragment.render(s);
        assertTrue(result.contains("관계 유형: 연인 · 썸"));
        assertTrue(result.contains("<conflict_category"));
        assertTrue(result.contains("</conflict_category>"));
    }

    @Test
    void render_includesCustomText_whenPresent() {
        Session s = sessionWithMajor("couple", "답장이 너무 짧아요");
        String result = fragment.render(s);
        assertTrue(result.contains("사용자 추가 설명: 답장이 너무 짧아요"));
    }

    @Test
    void render_omitsCustomText_whenBlank() {
        Session s = sessionWithMajor("couple", "   ");
        String result = fragment.render(s);
        assertFalse(result.contains("사용자 추가 설명"));
    }

    @Test
    void render_noteLabelDirectCitationWarning() {
        Session s = sessionWithMajor("couple", null);
        String result = fragment.render(s);
        assertTrue(result.contains("라벨을 직접 인용하지는 않습니다"));
    }

    // ── helpers ──────────────────────────────────────────────────

    private Session sessionWithMajor(String majorId, String customText) {
        Session s = new Session();
        Session.Category c = new Session.Category();
        c.majorId = majorId;
        c.customText = customText;
        s.setCategory(c);
        return s;
    }
}
