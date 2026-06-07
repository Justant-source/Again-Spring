package com.againspring.aiuser.orchestrator.api;

import com.againspring.aiuser.orchestrator.engine.planner.DailyPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final DailyPlanner dailyPlanner;

    @PostMapping("/plan-daily")
    public String planDaily() {
        log.info("Manual trigger: DailyPlanner.planForToday()");
        dailyPlanner.planForToday();
        return "Daily planning triggered";
    }
}
