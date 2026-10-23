package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.ai.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Every day 23:00 KST: Telegram summary of today's X growth loop (replies, follows, follower
 * delta). The follower delta is against the previous report's snapshot, kept in
 * {@code system_setting} as {@code yyyy-MM-dd|followers|following}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XDailyReportService {

    static final String SNAPSHOT_KEY = "marketing.x.report.last_counts";

    private final XOpsActionLedger ledger;
    private final AsmClient asmClient;
    private final TelegramNotifier telegramNotifier;
    private final SystemSettingRepository systemSettingRepository;

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Seoul")
    public void report() {
        try {
            telegramNotifier.send(buildReport(Instant.now()));
        } catch (Exception e) {
            log.warn("[x-report] failed: {}", e.getMessage());
        }
    }

    /** Also stores the new snapshot. Package-private for tests. */
    String buildReport(Instant now) {
        LocalDate today = now.atZone(XDailyQuota.KST).toLocalDate();
        int outbound = ledger.countPostedToday(XOpsAction.Kind.OUTBOUND, now);
        int inbound = ledger.countPostedToday(XOpsAction.Kind.INBOUND, now);
        int follows = ledger.countPostedToday(XOpsAction.Kind.FOLLOW, now);
        int failed = ledger.countTodayByStatus(XOpsAction.Kind.OUTBOUND, XOpsAction.Status.FAILED, now)
            + ledger.countTodayByStatus(XOpsAction.Kind.INBOUND, XOpsAction.Status.FAILED, now);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 [Again-Spring] X 일일 리포트 ").append(today).append('\n');
        sb.append("• 댓글 합계: ").append(outbound + inbound).append("건 (선댓글 ").append(outbound)
            .append(" + 대댓글 ").append(inbound).append(")\n");
        sb.append("• 게시 실패: ").append(failed).append("건\n");
        sb.append("• 맞팔로우: ").append(follows).append("명\n");

        try {
            AsmClient.XProfileCounts now2 = asmClient.getXProfileCounts();
            Optional<String[]> prev = readSnapshot();
            sb.append("• 팔로워: ").append(now2.followers());
            prev.ifPresentOrElse(p -> sb.append(" (").append(signed(now2.followers() - Integer.parseInt(p[1])))
                    .append(", ").append(p[0]).append(" 대비)"),
                () -> sb.append(" (비교 기준 없음 — 오늘이 기준점)"));
            sb.append('\n');
            sb.append("• 팔로잉: ").append(now2.following());
            prev.ifPresent(p -> sb.append(" (").append(signed(now2.following() - Integer.parseInt(p[2])))
                .append(')'));
            writeSnapshot(today, now2);
        } catch (Exception e) {
            log.warn("[x-report] profile counts unavailable: {}", e.getMessage());
            sb.append("• 팔로워/팔로잉: 조회 실패 (").append(e.getMessage()).append(')');
        }
        return sb.toString();
    }

    private static String signed(int d) {
        return d > 0 ? "+" + d : String.valueOf(d);
    }

    private Optional<String[]> readSnapshot() {
        return systemSettingRepository.findById(SNAPSHOT_KEY)
            .map(SystemSetting::getSettingValue)
            .map(v -> v.split("\\|"))
            .filter(a -> a.length == 3);
    }

    private void writeSnapshot(LocalDate day, AsmClient.XProfileCounts c) {
        SystemSetting row = systemSettingRepository.findById(SNAPSHOT_KEY)
            .orElseGet(() -> SystemSetting.builder().settingKey(SNAPSHOT_KEY).build());
        row.setSettingValue(day + "|" + c.followers() + "|" + c.following());
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy("x-daily-report");
        systemSettingRepository.save(row);
    }
}
