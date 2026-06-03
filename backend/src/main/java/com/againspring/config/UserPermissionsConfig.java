package com.againspring.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 사용자 등급별 권한·제한 정책을 시작 시 JSON에서 로드하는 설정 빈.
 *
 * 권위본: shared/docs/policies/user-permissions.json
 * 경로 오버라이드: app.user-permissions.path (env: USER_PERMISSIONS_PATH)
 *
 * 파일 누락 시 클래스패스 fallback (resources/policies/user-permissions.json),
 * 그것도 없으면 코드 내 안전 기본값 사용.
 */
@Slf4j
@Component
public class UserPermissionsConfig {

    @Value("${app.user-permissions.path:./shared/docs/policies/user-permissions.json}")
    private String configPath;

    private TierConfig guest;
    private TierConfig registered;
    private TierConfig admin;

    @PostConstruct
    public void load() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            UserPermissions root = readFromExternalPath(mapper);
            if (root == null) {
                root = readFromClasspath(mapper);
            }
            if (root == null) {
                log.warn("UserPermissions JSON 로드 실패 — 코드 기본값 사용");
                applyHardcodedDefaults();
                return;
            }
            this.guest = root.tiers.get("guest");
            this.registered = root.tiers.get("registered");
            this.admin = root.tiers.get("admin");
            if (this.guest == null || this.registered == null) {
                log.warn("UserPermissions JSON에 guest/registered 키가 없음 — 기본값 사용");
                applyHardcodedDefaults();
            } else {
                if (this.admin == null) {
                    // admin tier가 빠진 경우 registered 복제로 폴백 (관리자 진입은 false 강제)
                    this.admin = TierConfig.adminDefaults();
                    log.warn("UserPermissions JSON에 admin tier 누락 — 코드 기본값 사용");
                }
                log.info("UserPermissions loaded: guest.turnLimit={}, guest.dailyLimit={}, " +
                        "guest.tokenSec={}, guest.retentionDays={}, admin.canAccessDashboard={}",
                    guest.sessions.messageTurnLimit, guest.sessions.dailyLimit,
                    guest.auth.tokenExpirationSeconds, guest.data.messageContentRetentionDays,
                    admin.admin != null && admin.admin.canAccessDashboard);
            }
        } catch (Exception e) {
            log.error("UserPermissions 로드 중 예외 — 기본값 사용", e);
            applyHardcodedDefaults();
        }
    }

    private UserPermissions readFromExternalPath(ObjectMapper mapper) {
        try {
            Path p = Path.of(configPath);
            if (Files.exists(p)) {
                return mapper.readValue(Files.readAllBytes(p), UserPermissions.class);
            }
        } catch (Exception e) {
            log.warn("외부 경로 {} 읽기 실패: {}", configPath, e.getMessage());
        }
        return null;
    }

    private UserPermissions readFromClasspath(ObjectMapper mapper) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("policies/user-permissions.json")) {
            if (is != null) {
                return mapper.readValue(is, UserPermissions.class);
            }
        } catch (Exception e) {
            log.warn("classpath fallback 읽기 실패: {}", e.getMessage());
        }
        return null;
    }

    private void applyHardcodedDefaults() {
        this.guest = TierConfig.guestDefaults();
        this.registered = TierConfig.registeredDefaults();
        this.admin = TierConfig.adminDefaults();
    }

    public TierConfig getGuest() { return guest; }
    public TierConfig getRegistered() { return registered; }
    public TierConfig getAdmin() { return admin; }

    /** 사용자 등급 판별 후 적절한 tier 반환 (admin > registered > guest 우선순위) */
    public TierConfig forUser(boolean isGuest, java.util.Collection<String> roles) {
        if (isGuest) return guest;
        if (roles != null && roles.contains("ADMIN")) return admin;
        return registered;
    }

    /** Backward compatibility (게스트 여부만으로 판별 — 호출 측에서 admin 판별 못 함) */
    public TierConfig forUser(boolean isGuest) {
        return isGuest ? guest : registered;
    }

    // ─── DTO ─────────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserPermissions {
        private String version;
        private Map<String, TierConfig> tiers;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TierConfig {
        private String label;
        private String summary;
        private Auth auth;
        private Sessions sessions;
        private Profile profile;
        private RetentionData data;
        private Ui ui;
        private Mediator mediator;
        private Admin admin;

        static TierConfig guestDefaults() {
            TierConfig t = new TierConfig();
            t.label = "게스트"; t.summary = "fallback";
            t.auth = new Auth(); t.auth.tokenExpirationSeconds = 2592000; // 30일 — 디바이스 게스트 지속성
            t.sessions = new Sessions(); t.sessions.dailyLimit = 3;
            t.sessions.dailyLimitScope = "ip"; t.sessions.messageTurnLimit = 3;
            t.sessions.duoModeAllowed = false; t.sessions.canInvitePartner = false;
            t.profile = new Profile(); t.profile.canViewHistory = false;
            t.data = new RetentionData(); t.data.messageContentRetentionDays = 7;
            t.data.sessionRetentionDays = 30; t.data.reportRetentionDays = 30;
            t.ui = new Ui(); t.ui.showHistoryMenu = false;
            t.ui.showLandingChatEntry = true;
            t.ui.showCommunicationStyleSection = false;
            t.mediator = new Mediator(); t.mediator.styleSource = "default";
            t.mediator.defaultStyleX = 50; t.mediator.defaultStyleY = 50;
            return t;
        }

        static TierConfig registeredDefaults() {
            TierConfig t = new TierConfig();
            t.label = "회원"; t.summary = "fallback";
            t.auth = new Auth(); t.auth.tokenExpirationSeconds = 86400;
            t.auth.requiresEmailVerification = true; t.auth.requiresOnboarding = true;
            t.sessions = new Sessions(); t.sessions.dailyLimit = 5;
            t.sessions.dailyLimitScope = "user"; t.sessions.messageTurnLimit = null;
            t.sessions.duoModeAllowed = true; t.sessions.canInvitePartner = true;
            t.profile = new Profile(); t.profile.canViewHistory = true;
            t.profile.canEditNickname = true; t.profile.canCompleteOnboarding = true;
            t.data = new RetentionData(); t.data.messageContentRetentionDays = 30;
            t.data.sessionRetentionDays = 180;
            t.ui = new Ui(); t.ui.showHistoryMenu = true;
            t.ui.showLandingChatEntry = true;
            t.ui.showCommunicationStyleSection = true;
            t.mediator = new Mediator(); t.mediator.styleSource = "profile";
            t.mediator.defaultStyleX = 50; t.mediator.defaultStyleY = 50;
            t.admin = new Admin(); // 모두 false
            return t;
        }

        static TierConfig adminDefaults() {
            TierConfig t = registeredDefaults();
            t.label = "관리자"; t.summary = "fallback";
            t.ui.showAdminEntryButton = true;
            t.ui.showLandingChatEntry = false;
            t.ui.showCommunicationStyleSection = false;
            t.ui.showHistoryMenu = false;
            t.admin = new Admin();
            t.admin.canAccessDashboard = true;
            t.admin.canViewAllUsers = true;
            t.admin.canModifyFeedback = true;
            t.admin.canAnonymizeUser = true;
            t.admin.canViewCrisisMonitor = true;
            t.admin.canViewSystemHealth = true;
            t.admin.canAccessMarketing = true;
            return t;
        }
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Auth {
        private long tokenExpirationSeconds;
        private boolean requiresEmailVerification;
        private boolean requiresOnboarding;
        private boolean canChangePassword;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sessions {
        private Integer dailyLimit;
        private String dailyLimitScope; // "ip" | "user"
        private Integer messageTurnLimit; // null = unlimited
        private boolean duoModeAllowed;
        private boolean canInvitePartner;
        private boolean canResumeOldSession;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private boolean canEditNickname;
        private boolean canEditEmail;
        private boolean canEditCommunicationStyle;
        private boolean canCompleteOnboarding;
        private boolean canSetMbti;
        private boolean canViewHistory;
        private boolean canDeleteAccount;
        private boolean deleteRequiresPassword;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RetentionData {
        private Integer messageContentRetentionDays;
        private Integer sessionRetentionDays;
        private Integer reportRetentionDays; // null = forever
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ui {
        private boolean showHistoryMenu;
        private boolean showProfileEditing;
        private boolean showConsentReconfirmModal;
        private boolean showUpgradeModalOnLimit;
        private boolean showGuestModeBadge;
        private boolean showBetaBanner;
        private boolean showAdminEntryButton;
        private boolean showLandingChatEntry;
        private boolean showCommunicationStyleSection;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mediator {
        private String styleSource; // "default" | "profile" | "per_session"
        private Integer defaultStyleX;
        private Integer defaultStyleY;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Admin {
        private boolean canAccessDashboard;
        private boolean canViewAllUsers;
        private boolean canModifyFeedback;
        private boolean canAnonymizeUser;
        private boolean canViewCrisisMonitor;
        private boolean canViewSystemHealth;
        private boolean canAccessMarketing;
    }
}
