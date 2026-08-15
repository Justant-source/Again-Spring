package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketingDailyReportSchedulerTest {

    @Test
    void testParseTargets_SingleChannel() {
        List<String> result = MarketingDailyReportScheduler.parseTargets("[\"x_thread\"]");
        assertEquals(1, result.size());
        assertEquals("x_thread", result.get(0));
    }

    @Test
    void testParseTargets_MultipleChannels() {
        List<String> result = MarketingDailyReportScheduler.parseTargets("[\"instagram_reels\",\"youtube_shorts\"]");
        assertEquals(2, result.size());
        assertTrue(result.contains("instagram_reels"));
        assertTrue(result.contains("youtube_shorts"));
    }

    @Test
    void testParseTargets_Null() {
        List<String> result = MarketingDailyReportScheduler.parseTargets(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseTargets_Empty() {
        List<String> result = MarketingDailyReportScheduler.parseTargets("");
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseTargets_InvalidJson() {
        List<String> result = MarketingDailyReportScheduler.parseTargets("not valid json");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFormatDailyReport_AllChannels() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // x_thread: 8 created, 8 published, 0 failed, 0 waiting
        for (int i = 0; i < 8; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"x_thread\"]");
            job.setStatus("PUBLISHED");
            jobs.add(job);
        }

        // youtube_shorts: 12 created, 2 published, 5 failed, 5 waiting
        for (int i = 0; i < 2; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"youtube_shorts\"]");
            job.setStatus("PUBLISHED");
            jobs.add(job);
        }
        for (int i = 0; i < 5; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"youtube_shorts\"]");
            job.setStatus("FAILED");
            job.setFailureCode("VARIANT_LLM_ERROR");
            jobs.add(job);
        }
        for (int i = 0; i < 5; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"youtube_shorts\"]");
            job.setStatus("QUEUED");
            jobs.add(job);
        }

        // instagram_reels: 14 created, 1 published, 4 failed, 9 waiting
        for (int i = 0; i < 1; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"instagram_reels\"]");
            job.setStatus("PUBLISHED");
            jobs.add(job);
        }
        for (int i = 0; i < 4; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"instagram_reels\"]");
            job.setStatus("FAILED");
            job.setFailureCode("SIBOM_PLAN_TOO_SHORT");
            jobs.add(job);
        }
        for (int i = 0; i < 9; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"instagram_reels\"]");
            job.setStatus("RUNNING");
            jobs.add(job);
        }

        // instagram_feed: 1 created, 1 published, 0 failed, 0 waiting
        for (int i = 0; i < 1; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"instagram_feed\"]");
            job.setStatus("PUBLISHED");
            jobs.add(job);
        }

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Check total line
        assertTrue(report.contains("x_thread"));
        assertTrue(report.contains("youtube_shorts"));
        assertTrue(report.contains("instagram_reels"));
        assertTrue(report.contains("instagram_feed"));

        // Check basic structure exists
        assertTrue(report.contains("채널"));
        assertTrue(report.contains("생성"));
        assertTrue(report.contains("발행"));
        assertTrue(report.contains("실패"));
        assertTrue(report.contains("대기"));
        assertTrue(report.contains("전환율"));

        // Check failure codes section
        assertTrue(report.contains("실패 상위"));
        assertTrue(report.contains("VARIANT_LLM_ERROR") || report.contains("SIBOM_PLAN_TOO_SHORT"));

        // Should not mention zero published warning
        assertFalse(report.contains("⚠️ 오늘 발행 0건"));
    }

    @Test
    void testFormatDailyReport_ZeroPublished() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // All waiting, no published
        for (int i = 0; i < 5; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"x_thread\"]");
            job.setStatus("QUEUED");
            jobs.add(job);
        }

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Should have warning header
        assertTrue(report.startsWith("⚠️ 오늘 발행 0건"));
        assertTrue(report.contains(String.valueOf(today)));
    }

    @Test
    void testFormatDailyReport_NoJobs() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Should have all channels but with zeros
        assertTrue(report.contains("x_thread"));
        assertTrue(report.contains("youtube_shorts"));
        assertTrue(report.contains("instagram_reels"));
        assertTrue(report.contains("instagram_feed"));
        // Should mention zero published at the start
        assertTrue(report.contains("⚠️ 오늘 발행 0건"));
    }

    @Test
    void testFormatDailyReport_FailureCodeCounts() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // Create jobs with different failure codes
        for (int i = 0; i < 3; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"youtube_shorts\"]");
            job.setStatus("FAILED");
            job.setFailureCode("SIBOM_PLAN_TOO_SHORT");
            jobs.add(job);
        }

        for (int i = 0; i < 3; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"youtube_shorts\"]");
            job.setStatus("FAILED");
            job.setFailureCode("VARIANT_LLM_ERROR");
            jobs.add(job);
        }

        for (int i = 0; i < 3; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"instagram_reels\"]");
            job.setStatus("FAILED");
            job.setFailureCode("WAGGLE:RENDER_FAILED");
            jobs.add(job);
        }

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Check failure codes section
        assertTrue(report.contains("실패 상위"));
        assertTrue(report.contains("SIBOM_PLAN_TOO_SHORT"));
        assertTrue(report.contains("VARIANT_LLM_ERROR"));
        assertTrue(report.contains("WAGGLE:RENDER_FAILED"));
    }

    @Test
    void testFormatDailyReport_MultipleChannelsPerJob() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // A job targeting multiple channels
        MarketingJob job = new MarketingJob();
        job.setTargets("[\"instagram_reels\",\"youtube_shorts\"]");
        job.setStatus("PUBLISHED");
        jobs.add(job);

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Both channels should count this job
        assertTrue(report.contains("instagram_reels"));
        assertTrue(report.contains("youtube_shorts"));
        // Report should have channel data
        assertTrue(report.contains("채널"));
        assertTrue(report.contains("생성"));
    }

    @Test
    void testFormatDailyReport_NullFailureCode() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // Job with null failure code
        MarketingJob job = new MarketingJob();
        job.setTargets("[\"x_thread\"]");
        job.setStatus("FAILED");
        job.setFailureCode(null);
        jobs.add(job);

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Should still count the failure
        assertTrue(report.contains("실패"));
        // NULL should be in the failure codes
        assertTrue(report.contains("NULL"));
    }

    @Test
    void testFormatDailyReport_ConversionRate() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // 10 created, 5 published = 50% conversion rate
        for (int i = 0; i < 5; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"x_thread\"]");
            job.setStatus("PUBLISHED");
            jobs.add(job);
        }
        for (int i = 0; i < 5; i++) {
            MarketingJob job = new MarketingJob();
            job.setTargets("[\"x_thread\"]");
            job.setStatus("QUEUED");
            jobs.add(job);
        }

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        assertTrue(report.contains("전환율") && report.contains("50%"));
    }

    @Test
    void testFormatDailyReport_PartialStatus() {
        LocalDate today = LocalDate.now();
        List<MarketingJob> jobs = new ArrayList<>();

        // PARTIAL status should count as failed
        MarketingJob job = new MarketingJob();
        job.setTargets("[\"instagram_reels\"]");
        job.setStatus("PARTIAL");
        job.setFailureCode("PARTIAL_PUBLISH");
        jobs.add(job);

        String report = MarketingDailyReportScheduler.formatDailyReport(jobs, today);

        // Should count as 1 created and 1 failed
        assertTrue(report.contains("instagram_reels"));
        // Should have failure section
        assertTrue(report.contains("실패 상위"));
        // Failure codes should include PARTIAL
        assertTrue(report.contains("PARTIAL_PUBLISH"));
    }

    @Test
    void testChannelStatsAccumulator() {
        MarketingDailyReportScheduler.ChannelStats stats = new MarketingDailyReportScheduler.ChannelStats();

        // Add different statuses
        stats.addJob("PUBLISHED");
        stats.addJob("PUBLISHED");
        stats.addJob("FAILED");
        stats.addJob("QUEUED");
        stats.addJob("QUEUED");

        assertEquals(5, stats.created);
        assertEquals(2, stats.published);
        assertEquals(1, stats.failed);
        assertEquals(2, stats.waiting);
    }
}
