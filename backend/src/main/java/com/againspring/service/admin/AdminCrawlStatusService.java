package com.againspring.service.admin;

import com.againspring.api.dto.response.CrawlStatusResponse;
import com.againspring.service.ai.AiLearningBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 크롤 상태 조회 서비스.
 *
 * ai-user/learning 서비스로부터 최근 크롤 로그를 조회하고,
 * 24시간 내 저장 건수·마지막 성공 시각·실패 건수를 집계해 반환.
 *
 * 서버에서 시간 계산을 완료하므로 프론트엔드는 응답을 그대로 표시하기만 하면 됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCrawlStatusService {

    private final AiLearningBridge aiLearningBridge;

    /**
     * 크롤 신선도 조회 및 24시간 통계 계산.
     *
     * @return 크롤 상태 응답 (정상 조회이면 errorMessage=null, 조회 실패이면 errorMessage 포함)
     */
    public CrawlStatusResponse getCrawlStatus() {
        Instant now = Instant.now();
        Instant since24hAgo = now.minus(24, ChronoUnit.HOURS);

        List<AiLearningBridge.CrawlLog> logs = aiLearningBridge.getCrawlLogsWithFallback();

        // 에러 로그 감지 (source="ERROR")
        if (logs.size() == 1 && "ERROR".equals(logs.get(0).source())) {
            String errorMsg = logs.get(0).at();  // at 필드가 오류 메시지
            return CrawlStatusResponse.builder()
                .savedBySource24h(new HashMap<>())
                .lastSuccessfulAt(new HashMap<>())
                .checkedAt(now)
                .errorMessage(errorMsg)
                .stale(true)
                .failureCount24h(0)
                .build();
        }

        Map<String, Integer> savedBySource = new HashMap<>();
        Map<String, String> lastSuccessful = new HashMap<>();
        int totalFailures = 0;
        int totalSuccesses = 0;

        for (AiLearningBridge.CrawlLog crawlLog : logs) {
            try {
                // at 필드를 Instant로 파싱
                Instant logTime = Instant.parse(crawlLog.at());

                // 24시간 범위 필터
                if (logTime.isBefore(since24hAgo)) {
                    break;  // 로그가 시간 역순이므로 이 이후는 모두 범위 밖
                }

                String source = crawlLog.source();

                if ("SUCCESS".equals(crawlLog.status())) {
                    // 저장 건수 누적
                    savedBySource.merge(source, crawlLog.itemsSaved() != null ? crawlLog.itemsSaved() : 0, Integer::sum);
                    // 마지막 성공 시각 업데이트 (처음 만나는 것이 가장 최신)
                    lastSuccessful.putIfAbsent(source, crawlLog.at());
                    totalSuccesses++;
                } else if ("FAILED".equals(crawlLog.status())) {
                    totalFailures++;
                }
            } catch (Exception e) {
                log.warn("[AdminCrawlStatusService] failed to parse log entry: {}", crawlLog, e);
            }
        }

        boolean isStale = totalSuccesses == 0;

        return CrawlStatusResponse.builder()
            // FE(CrawlStatusResponse.ts)가 이 두 필드를 항상 존재하는 Record로 취급하므로
            // 빈 맵이라도 null(=JSON에서 필드 자체 생략)로 만들면 안 된다 — 크롤이 0건일 때
            // (배지가 정확히 감지해야 하는 상황) FE가 undefined.values()로 크래시하게 된다.
            .savedBySource24h(savedBySource)
            .lastSuccessfulAt(lastSuccessful)
            .failureCount24h(totalFailures)
            .stale(isStale)
            .checkedAt(now)
            .errorMessage(null)
            .build();
    }
}
