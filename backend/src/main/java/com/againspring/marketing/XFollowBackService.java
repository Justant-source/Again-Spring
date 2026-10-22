package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Follows back verified (blue-check) followers in small irregular batches (1–3, driven by
 * {@link XHumanPacer} roughly every 20–55 min). Today's target is a date-seeded draw in
 * 60–100% of {@code follow_back_daily_cap}. Followed accounts
 * show up in the Following timeline, which is the outbound reply candidate source.
 *
 * <p>Gate: {@code marketing.x.follow_back_enabled} (default <b>false</b>, DB only).
 * Knobs: {@code follow_back_daily_cap} (default 30, 0–100, KST day) and
 * {@code follow_back_per_tick} (default 5, 1–10). Every follow is one
 * {@link XOpsAction.Kind#FOLLOW} ledger row ({@code follow:<handle>}), so an account
 * is never followed twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XFollowBackService {

    public static final String KEY_ENABLED = "marketing.x.follow_back_enabled";
    public static final String KEY_DAILY_CAP = "marketing.x.follow_back_daily_cap";
    public static final String KEY_PER_TICK = "marketing.x.follow_back_per_tick";
    static final int DEFAULT_DAILY_CAP = 30;
    static final int DEFAULT_PER_TICK = 5;

    private final SystemSettingRepository systemSettingRepository;
    private final AsmClient asmClient;
    private final XOpsActionLedger ledger;

    public void run(Instant now) {
        if (!readBool(KEY_ENABLED)) {
            return;
        }
        int dailyCap = readInt(KEY_DAILY_CAP, DEFAULT_DAILY_CAP, 0, 100);
        int perTick = readInt(KEY_PER_TICK, DEFAULT_PER_TICK, 1, 10);
        int todayTarget = XDailyQuota.pick(
            now.atZone(XDailyQuota.KST).toLocalDate(), (int) Math.ceil(dailyCap * 0.6), dailyCap);
        int remaining = todayTarget - ledger.countPostedToday(XOpsAction.Kind.FOLLOW, now);
        if (remaining <= 0) {
            return;
        }
        perTick = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.min(3, perTick));
        AsmClient.XFollowBackResult result;
        try {
            result = asmClient.followBackVerified(Math.min(perTick, remaining));
        } catch (Exception e) {
            log.warn("[x-followback] failed: {}", e.getMessage());
            return;
        }
        int recorded = 0;
        for (String raw : result.followed()) {
            String handle = XOpsActionLedger.normHandle(raw);
            if (handle == null) {
                continue;
            }
            String sentinel = "follow:" + handle;
            if (ledger.alreadyHandled(sentinel)) {
                continue;
            }
            ledger.recordPosted(XOpsAction.Kind.FOLLOW, sentinel, null, null, null, handle, now);
            recorded++;
        }
        log.info("[x-followback] seen={} alreadyFollowing={} followed={} recorded={}",
            result.seen(), result.alreadyFollowing(), result.followed().size(), recorded);
    }

    private boolean readBool(String key) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(v -> v != null && ("true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim())))
            .orElse(false);
    }

    private int readInt(String key, int def, int min, int max) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .map(raw -> {
                try {
                    int n = Integer.parseInt(raw.trim());
                    return n < min || n > max ? def : n;
                } catch (Exception e) {
                    return def;
                }
            })
            .orElse(def);
    }
}
