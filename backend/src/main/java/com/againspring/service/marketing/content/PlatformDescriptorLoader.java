package com.againspring.service.marketing.content;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.againspring.domain.marketing.MarketingContent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads platform-descriptors.yml from classpath at startup.
 * Fails fast if fewer than 5 entries are present (misconfiguration guard).
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Slf4j
public class PlatformDescriptorLoader {

    private final Map<MarketingContent.Platform, PlatformDescriptor> descriptors = new LinkedHashMap<>();

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        String path = "marketing/platform-descriptors.yml";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new BeanInitializationException("Resource not found: " + path);
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            List<Map<String, Object>> list = (List<Map<String, Object>>) root.get("platforms");
            for (Map<String, Object> entry : list) {
                String code = (String) entry.get("code");
                MarketingContent.Platform platform = codeToEnum(code);
                PlatformDescriptor descriptor = PlatformDescriptor.builder()
                        .code(code)
                        .displayName((String) entry.get("displayName"))
                        .maxCharsPerUnit(toInt(entry.get("maxCharsPerUnit")))
                        .maxUnits(toInt(entry.get("maxUnits")))
                        .hashtagCount(toInt(entry.get("hashtagCount")))
                        .renderType(PlatformDescriptor.RenderType.valueOf((String) entry.get("renderType")))
                        .enabled(Boolean.TRUE.equals(entry.get("enabled")))
                        .build();
                descriptors.put(platform, descriptor);
                log.info("Loaded platform descriptor: {} (enabled={})", code, descriptor.isEnabled());
            }
        } catch (BeanInitializationException e) {
            throw e;
        } catch (Exception e) {
            throw new BeanInitializationException("Failed to load " + path, e);
        }
        if (descriptors.size() < 5) {
            throw new BeanInitializationException(
                    "platform-descriptors.yml must declare at least 5 platforms, found: " + descriptors.size());
        }
    }

    public Map<MarketingContent.Platform, PlatformDescriptor> getAll() {
        return Map.copyOf(descriptors);
    }

    public PlatformDescriptor get(MarketingContent.Platform platform) {
        PlatformDescriptor d = descriptors.get(platform);
        if (d == null) {
            throw new IllegalArgumentException("No descriptor registered for platform: " + platform);
        }
        return d;
    }

    private MarketingContent.Platform codeToEnum(String code) {
        return switch (code.toLowerCase()) {
            case "x" -> MarketingContent.Platform.X;
            case "instagram" -> MarketingContent.Platform.INSTAGRAM;
            case "naver_blog" -> MarketingContent.Platform.NAVER_BLOG;
            case "threads" -> MarketingContent.Platform.THREADS;
            case "facebook" -> MarketingContent.Platform.FACEBOOK;
            default -> throw new BeanInitializationException("Unknown platform code in YAML: " + code);
        };
    }

    private int toInt(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}
