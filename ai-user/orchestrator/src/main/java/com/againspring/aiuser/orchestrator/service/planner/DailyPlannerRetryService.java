package com.againspring.aiuser.orchestrator.service.planner;

import com.againspring.aiuser.orchestrator.domain.DailyPlannerRetryLog;
import com.againspring.aiuser.orchestrator.engine.planner.DailyPlanner;
import com.againspring.aiuser.orchestrator.repository.DailyPlannerRetryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPlannerRetryService {

    private final DailyPlannerRetryLogRepository retryLogRepository;
    private final DailyPlanner dailyPlanner;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 어제 실패한 계획이 있으면 1회 재시도 수행.
     * 최대 1회 재시도만 — 2회차는 금지.
     *
     * @return true if retry was attempted (success or final failure)
     */
    public boolean checkAndRetryFailedYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);

        Optional<DailyPlannerRetryLog> logEntry = retryLogRepository.findByDayBucket(yesterday);
        if (logEntry.isEmpty()) {
            log.debug("DailyPlannerRetryService: no log for {}", yesterday);
            return false;
        }

        DailyPlannerRetryLog entry = logEntry.get();

        // 이미 성공했거나 2회차 실패했으면 스킵
        if ("SUCCESS".equals(entry.getStatus())) {
            log.debug("DailyPlannerRetryService: {} already SUCCESS", yesterday);
            return false;
        }
        if (entry.getAttemptCount() >= 2) {
            log.warn("DailyPlannerRetryService: {} already attempted {} times, giving up", yesterday, entry.getAttemptCount());
            return false;
        }

        // 1회 재시도
        log.info("DailyPlannerRetryService: retrying for {} (attempt {}/2)", yesterday, entry.getAttemptCount() + 1);
        try {
            dailyPlanner.planForToday();

            entry.setStatus("SUCCESS");
            entry.setAttemptCount(2);
            entry.setRetryAttemptedAt(Instant.now());
            retryLogRepository.save(entry);

            log.info("DailyPlannerRetryService: retry SUCCESS for {}", yesterday);
            return true;

        } catch (Exception e) {
            log.error("DailyPlannerRetryService: retry FAILED for {} - {}", yesterday, e.getMessage(), e);

            entry.setStatus("FAILED");
            entry.setAttemptCount(2);
            entry.setErrorMessage(e.getMessage());
            entry.setErrorClass(e.getClass().getName());
            entry.setStacktraceExcerpt(truncateStacktrace(e));
            entry.setRetryAttemptedAt(Instant.now());
            retryLogRepository.save(entry);

            return true;  // 재시도는 수행했음 (결과는 FAILED)
        }
    }

    /**
     * 재시도 로그 기록 (1차 실패 시).
     */
    public void recordInitialFailure(Exception e) {
        LocalDate today = LocalDate.now(KST);

        // 이미 기록되어 있으면 업데이트하지 말고 스킵 (중복 기록 방지)
        Optional<DailyPlannerRetryLog> existing = retryLogRepository.findByDayBucket(today);
        if (existing.isPresent()) {
            log.debug("DailyPlannerRetryService: retry log already exists for {}", today);
            return;
        }

        DailyPlannerRetryLog entry = DailyPlannerRetryLog.builder()
            .dayBucket(today)
            .attemptCount(1)
            .status("FAILED")
            .errorMessage(e.getMessage())
            .errorClass(e.getClass().getName())
            .stacktraceExcerpt(truncateStacktrace(e))
            .previousAttemptAt(Instant.now())
            .build();

        retryLogRepository.save(entry);
        log.info("DailyPlannerRetryService: recorded initial failure for {}", today);
    }

    private String truncateStacktrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        return full.length() > 500 ? full.substring(0, 500) : full;
    }
}
