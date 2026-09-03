package com.againspring.safety;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 실사용자 입력의 위기 키워드(자살·자해·폭력·성폭력·아동학대) 관제.
 * 게시를 막지 않는다 — CrisisDetectedEvent → SafetyAuditLogger 감사 로그만. AI-user 본문에는 호출하지 않는다.
 */
@Slf4j
@Component
public class CrisisKeywordGuard {
    @Value("${app.safety.crisis-keywords-path:classpath:/safety/crisis-keywords.yml}")
    private String configPath;

    private final List<String> keywords = new ArrayList<>();

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void loadKeywords() {
        keywords.clear();
        try (InputStream in = new DefaultResourceLoader().getResource(configPath).getInputStream()) {
            Map<String, Object> config = new Yaml().load(in);
            List<Map<String, Object>> list = (List<Map<String, Object>>) config.get("crisis_keywords");
            if (list != null) for (Map<String, Object> item : list) keywords.add(String.valueOf(item.get("pattern")).toLowerCase(Locale.ROOT));
            log.info("Crisis keywords loaded: {}", keywords.size());
        } catch (Exception e) {
            throw new IllegalStateException("cannot load crisis keywords from " + configPath, e);
        }
    }

    public CrisisScanResult scan(String text) {
        if (text == null || text.isEmpty()) return CrisisScanResult.none();
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String k : keywords) if (lower.contains(k)) hits.add(k);
        return hits.isEmpty() ? CrisisScanResult.none() : new CrisisScanResult(true, List.copyOf(hits));
    }
}
