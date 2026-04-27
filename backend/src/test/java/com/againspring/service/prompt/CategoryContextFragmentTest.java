package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import com.againspring.service.category.CategoryCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

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
        Session s = sessionWithCategory("nonexistent", null, null, null);
        assertEquals("", fragment.render(s));
    }

    @Test
    void render_includesMajorLabel_withoutMiddle() {
        Session s = sessionWithCategory("couple", null, null, null);
        String result = fragment.render(s);
        assertTrue(result.contains("관계 유형: 연인 · 썸"));
        assertTrue(result.contains("<conflict_category"));
        assertTrue(result.contains("</conflict_category>"));
    }

    @Test
    void render_includesMiddleLabel_whenPresent() {
        Session s = sessionWithCategory("couple", "couple_contact", null, null);
        String result = fragment.render(s);
        assertTrue(result.contains("갈등 카테고리: 연락 · 관심"));
        assertFalse(result.contains("연락 · 관심 >"), "minor 없으면 > 구분자 없음");
    }

    @Test
    void render_includesAllThreeLevels() {
        Session s = sessionWithCategory("marriage", "marriage_inlaws", "visit_freq", null);
        String result = fragment.render(s);
        assertTrue(result.contains("관계 유형: 부부"));
        assertTrue(result.contains("갈등 카테고리: 시가 · 처가 > 시가/처가 방문 빈도"));
    }

    @Test
    void render_omitsCustomMinorLabel_butIncludesCustomText() {
        Session s = sessionWithCategory("couple", "couple_contact", "custom", "답장이 너무 짧아요");
        String result = fragment.render(s);
        assertFalse(result.contains("직접 입력"), "custom minor 라벨은 노출하지 않음");
        assertTrue(result.contains("사용자 추가 설명: 답장이 너무 짧아요"));
    }

    @Test
    void render_includesCustomText_evenWithoutMinor() {
        Session s = sessionWithCategory("couple", "couple_contact", null, "내가 원하는 건 따뜻한 답장");
        String result = fragment.render(s);
        assertTrue(result.contains("사용자 추가 설명: 내가 원하는 건 따뜻한 답장"));
    }

    @Test
    void render_noCustomText_whenBlank() {
        Session s = sessionWithCategory("couple", "couple_contact", "contact_too_little", "   ");
        String result = fragment.render(s);
        assertFalse(result.contains("사용자 추가 설명"));
    }

    @Test
    void render_noteLabelDirectCitationWarning() {
        Session s = sessionWithCategory("couple", "couple_contact", "contact_too_little", null);
        String result = fragment.render(s);
        assertTrue(result.contains("라벨을 직접 인용하지는 않습니다"));
    }

    // ── helpers ──────────────────────────────────────────────────

    private Session sessionWithCategory(String majorId, String middleId, String minorId, String customText) {
        Session s = new Session();
        Session.Category c = new Session.Category();
        c.majorId = majorId;
        c.middleId = middleId;
        c.minorId = minorId;
        c.customText = customText;
        s.setCategory(c);
        return s;
    }
}
