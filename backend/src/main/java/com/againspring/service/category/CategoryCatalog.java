package com.againspring.service.category;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 라벨 catalog. shared/docs/categories.yml 로드.
 * 권위본: shared/docs/policies/categories.md
 *
 * 사용처: CategoryContextFragment 가 categoryId → 한국어 라벨 변환에 사용.
 * catalog 로드 실패 시 빈 반환 — 채팅 회귀 없음.
 */
@Slf4j
@Component
public class CategoryCatalog {

    private final ResourceLoader resourceLoader;
    private final String catalogPath;

    private final Map<String, MajorCategory> byMajorId = new HashMap<>();

    public CategoryCatalog(
            ResourceLoader resourceLoader,
            @Value("${app.categories.path:./shared/docs/categories.yml}") String catalogPath) {
        this.resourceLoader = resourceLoader;
        this.catalogPath = catalogPath;
    }

    @PostConstruct
    public void load() {
        // classpath: prefix 그대로 사용 (테스트/운영 모두 지원). 그 외는 file: prefix 추가.
        String resourcePath = catalogPath.startsWith("classpath:") ? catalogPath : "file:" + catalogPath;
        try (InputStream is = resourceLoader.getResource(resourcePath).getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            List<?> majors = (List<?>) root.get("majors");
            if (majors == null) {
                log.warn("Category catalog has no 'majors' key: {}", catalogPath);
                return;
            }
            for (Object majorObj : majors) {
                MajorCategory major = parseMajor((Map<?, ?>) majorObj);
                byMajorId.put(major.getId(), major);
            }
            log.info("Category catalog loaded: {} majors from {}", byMajorId.size(), catalogPath);
        } catch (Exception e) {
            log.error("Failed to load category catalog from {} — category context will be empty", catalogPath, e);
        }
    }

    public MajorCategory getMajor(String majorId) {
        if (majorId == null) return null;
        return byMajorId.get(majorId);
    }

    public MiddleCategory getMiddle(String majorId, String middleId) {
        MajorCategory major = getMajor(majorId);
        if (major == null || middleId == null) return null;
        return major.getMiddles().stream()
                .filter(m -> middleId.equals(m.getId()))
                .findFirst()
                .orElse(null);
    }

    public MinorCategory getMinor(String majorId, String middleId, String minorId) {
        MiddleCategory middle = getMiddle(majorId, middleId);
        if (middle == null || minorId == null) return null;
        return middle.getMinors().stream()
                .filter(m -> minorId.equals(m.getId()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private MajorCategory parseMajor(Map<?, ?> map) {
        String id = (String) map.get("id");
        String label = (String) map.get("label");
        String relationType = (String) map.get("relationType");
        List<MiddleCategory> middles = new ArrayList<>();
        List<?> middleList = (List<?>) map.get("middles");
        if (middleList != null) {
            for (Object midObj : middleList) {
                middles.add(parseMiddle((Map<?, ?>) midObj));
            }
        }
        return new MajorCategory(id, label, relationType, middles);
    }

    @SuppressWarnings("unchecked")
    private MiddleCategory parseMiddle(Map<?, ?> map) {
        String id = (String) map.get("id");
        String label = (String) map.get("label");
        List<MinorCategory> minors = new ArrayList<>();
        List<?> minorList = (List<?>) map.get("minors");
        if (minorList != null) {
            for (Object minObj : minorList) {
                minors.add(parseMinor((Map<?, ?>) minObj));
            }
        }
        return new MiddleCategory(id, label, minors);
    }

    private MinorCategory parseMinor(Map<?, ?> map) {
        String id = (String) map.get("id");
        String label = (String) map.get("label");
        Boolean allowCustomInput = (Boolean) map.get("allowCustomInput");
        return new MinorCategory(id, label, Boolean.TRUE.equals(allowCustomInput));
    }

    // ── Inner POJOs ──────────────────────────────────────────────

    public static class MajorCategory {
        private final String id;
        private final String label;
        private final String relationType;
        private final List<MiddleCategory> middles;

        public MajorCategory(String id, String label, String relationType, List<MiddleCategory> middles) {
            this.id = id;
            this.label = label;
            this.relationType = relationType;
            this.middles = middles;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public String getRelationType() { return relationType; }
        public List<MiddleCategory> getMiddles() { return middles; }
    }

    public static class MiddleCategory {
        private final String id;
        private final String label;
        private final List<MinorCategory> minors;

        public MiddleCategory(String id, String label, List<MinorCategory> minors) {
            this.id = id;
            this.label = label;
            this.minors = minors;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public List<MinorCategory> getMinors() { return minors; }
    }

    public static class MinorCategory {
        private final String id;
        private final String label;
        private final boolean allowCustomInput;

        public MinorCategory(String id, String label, boolean allowCustomInput) {
            this.id = id;
            this.label = label;
            this.allowCustomInput = allowCustomInput;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        public boolean isAllowCustomInput() { return allowCustomInput; }
    }
}
