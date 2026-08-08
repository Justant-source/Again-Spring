package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.MarketingPinFormat;
import com.againspring.repository.ai.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingPlatformAutoServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private MarketingPlatformAutoService service;

    @Test
    void listPlatforms_defaults_supportedOnUnsupportedOff() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        List<MarketingPlatformAutoService.PlatformStatus> list = service.listPlatforms();

        assertThat(list).hasSize(MarketingPlatformAutoService.ALL_PLATFORMS.size());
        assertThat(list).extracting(MarketingPlatformAutoService.PlatformStatus::platform)
            .containsExactlyElementsOf(MarketingPlatformAutoService.ALL_PLATFORMS);

        for (MarketingPlatformAutoService.PlatformStatus row : list) {
            boolean supported = MarketingPlatformAutoService.RUNTIME_SUPPORTED.contains(row.platform());
            assertThat(row.runtimeSupported()).isEqualTo(supported);
            assertThat(row.autoEnabled()).isEqualTo(supported);
            if (supported) {
                assertThat(row.warning()).isNull();
            } else {
                assertThat(row.warning()).isNull(); // off by default → no warning
            }
        }
    }

    @Test
    void setAutoEnabled_unsupportedOn_returnsWarning() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        MarketingPlatformAutoService.PlatformStatus status =
            service.setAutoEnabled("naver_blog", true, "admin@test");

        assertThat(status.platform()).isEqualTo("naver_blog");
        assertThat(status.autoEnabled()).isTrue();
        assertThat(status.runtimeSupported()).isFalse();
        assertThat(status.warning()).isEqualTo(MarketingPlatformAutoService.UNSUPPORTED_ENABLE_WARNING);

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey())
            .isEqualTo("marketing.platform.naver_blog.auto_enabled");
        assertThat(captor.getValue().getSettingValue()).isEqualTo("true");
    }

    @Test
    void setAutoEnabled_supportedOff_noWarning() {
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());

        MarketingPlatformAutoService.PlatformStatus status =
            service.setAutoEnabled("x_thread", false, "admin");

        assertThat(status.autoEnabled()).isFalse();
        assertThat(status.runtimeSupported()).isTrue();
        assertThat(status.warning()).isNull();
    }

    @Test
    void setAutoEnabled_unknownPlatform_throws400() {
        assertThatThrownBy(() -> service.setAutoEnabled("myspace", true, "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unknown platform");
    }

    @Test
    void resolveTargets_text_skipsUnsupportedAndVideo() {
        List<String> enabled = List.of(
            "x_thread", "instagram_feed", "instagram_reels", "naver_blog", "threads");

        List<String> targets = service.resolveTargets(MarketingPublishFormat.TEXT, enabled);

        assertThat(targets).containsExactly("x_thread", "instagram_feed");
        assertThat(targets).doesNotContain("instagram_reels", "naver_blog", "threads");
    }

    @Test
    void resolveTargets_video_excludesFeedWhenReelsIncluded() {
        List<String> enabled = List.of(
            "x_thread", "instagram_feed", "instagram_reels", "youtube_shorts", "naver_clip");

        List<String> targets = service.resolveTargets(MarketingPublishFormat.VIDEO, enabled);

        assertThat(targets).containsExactly(
            "instagram_reels", "youtube_shorts", "x_thread");
        assertThat(targets).doesNotContain("instagram_feed", "naver_clip", "x");
    }

    @Test
    void resolveTargets_video_keepsFeedWhenReelsOff() {
        List<String> enabled = List.of("instagram_feed", "youtube_shorts", "x_thread");

        List<String> targets = service.resolveTargets(MarketingPublishFormat.VIDEO, enabled);

        assertThat(targets).containsExactly("youtube_shorts", "x_thread", "instagram_feed");
    }

    @Test
    void listPlatforms_readsStoredOverride() {
        when(systemSettingRepository.findById(any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if ("marketing.platform.x_thread.auto_enabled".equals(key)) {
                return Optional.of(SystemSetting.builder()
                    .settingKey(key).settingValue("false").build());
            }
            if ("marketing.platform.naver_blog.auto_enabled".equals(key)) {
                return Optional.of(SystemSetting.builder()
                    .settingKey(key).settingValue("true").build());
            }
            return Optional.empty();
        });

        List<MarketingPlatformAutoService.PlatformStatus> list = service.listPlatforms();
        MarketingPlatformAutoService.PlatformStatus xThread = list.stream()
            .filter(p -> "x_thread".equals(p.platform())).findFirst().orElseThrow();
        MarketingPlatformAutoService.PlatformStatus naver = list.stream()
            .filter(p -> "naver_blog".equals(p.platform())).findFirst().orElseThrow();

        assertThat(xThread.autoEnabled()).isFalse();
        assertThat(naver.autoEnabled()).isTrue();
        assertThat(naver.warning()).isEqualTo(MarketingPlatformAutoService.UNSUPPORTED_ENABLE_WARNING);
    }

    @Test
    void publishFormat_fromPin_mapsOneToOne() {
        assertThat(MarketingPublishFormat.fromPin(MarketingPinFormat.VIDEO))
            .isEqualTo(MarketingPublishFormat.VIDEO);
        assertThat(MarketingPublishFormat.fromPin(MarketingPinFormat.TEXT))
            .isEqualTo(MarketingPublishFormat.TEXT);
        assertThatThrownBy(() -> MarketingPublishFormat.fromPin(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
