package com.againspring.aiuser.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** ProviderHealthRegistry의 TTL 계산 등에 쓰는 실제 진행 시계. 테스트는 MutableClock 등으로 대체한다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
