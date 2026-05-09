package com.againspring.service.admin;

import com.againspring.repository.DailyStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RetentionCohortService {

    private final DailyStatsRepository dailyStatsRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLast14DaysRetention() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDate from = today.minusDays(13);
        var stats = dailyStatsRepository.findByStatDateBetweenOrderByStatDateAsc(from, today);

        List<Map<String, Object>> result = new ArrayList<>();
        for (var s : stats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", s.getStatDate().toString());
            row.put("dau", s.getDau());
            row.put("newUsers", s.getNewUsers());
            row.put("retentionRate", s.getNewUsers() > 0
                    ? Math.round((double) s.getDau() / s.getNewUsers() * 10000) / 100.0
                    : 0.0);
            result.add(row);
        }
        return result;
    }
}
