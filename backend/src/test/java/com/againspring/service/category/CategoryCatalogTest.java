package com.againspring.service.category;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class CategoryCatalogTest {

    private CategoryCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new CategoryCatalog(
                new DefaultResourceLoader(),
                "classpath:test-categories.yml");
        catalog.load();
    }

    @Test
    void load_parsesMajorLabels() {
        assertNotNull(catalog.getMajor("couple"));
        assertEquals("연인 · 썸", catalog.getMajor("couple").getLabel());
        assertEquals("부부", catalog.getMajor("marriage").getLabel());
    }

    @Test
    void getMiddle_returnsCorrectLabel() {
        CategoryCatalog.MiddleCategory middle = catalog.getMiddle("couple", "couple_contact");
        assertNotNull(middle);
        assertEquals("연락 · 관심", middle.getLabel());
    }

    @Test
    void getMinor_returnsCorrectLabel() {
        CategoryCatalog.MinorCategory minor = catalog.getMinor("couple", "couple_contact", "contact_too_little");
        assertNotNull(minor);
        assertEquals("연락이 너무 적어서 서운함", minor.getLabel());
        assertFalse(minor.isAllowCustomInput());
    }

    @Test
    void getMinor_customAllowInput() {
        CategoryCatalog.MinorCategory custom = catalog.getMinor("couple", "couple_contact", "custom");
        assertNotNull(custom);
        assertTrue(custom.isAllowCustomInput());
    }

    @Test
    void getMajor_returnsNull_whenIdNotMatched() {
        assertNull(catalog.getMajor("nonexistent"));
    }

    @Test
    void getMiddle_returnsNull_whenIdNotMatched() {
        assertNull(catalog.getMiddle("couple", "nonexistent_middle"));
    }

    @Test
    void getMinor_returnsNull_whenIdNotMatched() {
        assertNull(catalog.getMinor("couple", "couple_contact", "nonexistent_minor"));
    }
}
