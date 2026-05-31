package com.againspring.service.marketing.social;

import com.againspring.domain.marketing.SocialSession;
import com.againspring.repository.marketing.SocialSessionRepository;
import com.againspring.security.crypto.SocialCryptoService;
import com.againspring.service.notify.SocialOperatorNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SessionHealthCheckJob {

    private final SocialSessionRepository sessionRepo;
    private final SocialCryptoService crypto;
    private final SocialPosterClient posterClient;
    private final SocialOperatorNotifier notifier;

    /**
     * 매일 03:00 실행 — 세션 유효성 확인 + 갱신
     * 포스팅이 없는 날에도 피드를 잠깐 방문해 쿠키 만료를 리셋한다.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void checkSessionHealth() {
        log.info("[SESSION_HEALTH] Starting daily session health check & warm-keep");
        for (String platform : List.of("X", "INSTAGRAM")) {
            sessionRepo.findByPlatform(platform).ifPresent(session -> {
                if (session.getStatus() == SocialSession.SessionStatus.EXPIRED) {
                    log.info("[SESSION_HEALTH] platform={} already EXPIRED, skipping", platform);
                    return;
                }
                try {
                    String storageStateJson = crypto.decryptString(session.getStorageStateEnc());
                    Map<String, Object> result = posterClient.checkSessionHealth(platform, storageStateJson);

                    Boolean loggedIn = (Boolean) result.get("loggedIn");
                    String updatedStorageState = (String) result.get("updatedStorageState");

                    if (!Boolean.TRUE.equals(loggedIn)) {
                        session.setStatus(SocialSession.SessionStatus.EXPIRED);
                        sessionRepo.save(session);
                        notifier.notifyHealthCheckFailed(platform);
                        log.warn("[SESSION_HEALTH] platform={} session expired, notified operator", platform);
                    } else {
                        // 세션 갱신 — 피드 방문으로 새로워진 쿠키를 DB에 저장
                        if (updatedStorageState != null && !updatedStorageState.isBlank()) {
                            session.setStorageStateEnc(crypto.encryptString(updatedStorageState));
                            session.setLastUsedAt(Instant.now());
                            sessionRepo.save(session);
                            log.info("[SESSION_HEALTH] platform={} session healthy & refreshed", platform);
                        } else {
                            log.info("[SESSION_HEALTH] platform={} session healthy", platform);
                        }
                    }
                } catch (Exception e) {
                    log.error("[SESSION_HEALTH] check failed for platform={}: {}", platform, e.getMessage());
                    notifier.notifyHealthCheckFailed(platform);
                }
            });
        }
    }
}
