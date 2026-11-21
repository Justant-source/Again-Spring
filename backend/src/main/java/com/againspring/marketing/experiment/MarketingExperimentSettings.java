package com.againspring.marketing.experiment;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** 포맷 실험 system_setting 읽기·쓰기. 기본값은 전부 꺼짐. */
@Component
@RequiredArgsConstructor
public class MarketingExperimentSettings {

    public static final String KEY_ENABLED = "marketing.experiment.enabled";
    public static final String KEY_TOPIC_BIAS = "marketing.experiment.topic_bias_enabled";
    public static final String KEY_MIN_SAMPLES = "marketing.experiment.min_samples";
    public static final int DEFAULT_MIN_SAMPLES = 8;

    private final SystemSettingRepository repo;

    public boolean isEnabled() {
        return readBool(KEY_ENABLED);
    }

    public boolean isTopicBiasEnabled() {
        return readBool(KEY_TOPIC_BIAS);
    }

    public int minSamples() {
        return repo.findById(KEY_MIN_SAMPLES)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    int v = (int) Math.round(Double.parseDouble(raw.trim()));
                    return v < 1 ? DEFAULT_MIN_SAMPLES : v;
                } catch (Exception e) {
                    return DEFAULT_MIN_SAMPLES;
                }
            })
            .orElse(DEFAULT_MIN_SAMPLES);
    }

    @Transactional
    public void setEnabled(boolean v) {
        save(KEY_ENABLED, v);
    }

    @Transactional
    public void setTopicBiasEnabled(boolean v) {
        save(KEY_TOPIC_BIAS, v);
    }

    private boolean readBool(String key) {
        return repo.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                if (raw == null) {
                    return false;
                }
                String v = raw.trim().toLowerCase(Locale.ROOT);
                return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
            })
            .orElse(false);
    }

    private void save(String key, boolean value) {
        SystemSetting s = repo.findById(key).orElseGet(() -> SystemSetting.builder().settingKey(key).build());
        s.setSettingValue(value ? "true" : "false");
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy("marketing.experiment");
        repo.save(s);
    }
}
