package com.againspring.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 마케팅 시뮬레이션 비동기 실행 설정.
 * V15.3: 시뮬레이션을 비동기 스레드풀에서 실행하기 위한 설정.
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class MarketingAsyncConfig {

    @Bean("marketingExecutor")
    public Executor marketingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("marketing-sim-");
        executor.initialize();
        return executor;
    }
}
