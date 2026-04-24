package com.againspring.llm.bridge;

import com.againspring.llm.bridge.exception.LLMCapacityException;
import com.againspring.llm.bridge.exception.LLMTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ClaudeCodeWorkerPoolTest {

    private ClaudeCodeWorkerPool pool;

    @BeforeEach
    void setUp() {
        pool = new ClaudeCodeWorkerPool();
        ReflectionTestUtils.setField(pool, "poolSize", 2);
        ReflectionTestUtils.setField(pool, "permitAcquireTimeoutMs", 500L);
        pool.init();
    }

    @Test
    void testSuccessfulExecution() throws Exception {
        String result = pool.execute(
                () -> "success",
                Duration.ofSeconds(5),
                "corr-123"
        );
        assertThat(result).isEqualTo("success");
    }

    @Test
    void testMetricsTracking() throws Exception {
        pool.execute(() -> "task1", Duration.ofSeconds(5), "corr-1");
        assertThat(pool.getTasksCompleted()).isEqualTo(1);
        assertThat(pool.getAvailablePermits()).isEqualTo(2);
    }

    @Test
    void testTimeoutException() {
        assertThatThrownBy(() ->
                pool.execute(
                        () -> {
                            Thread.sleep(2000);
                            return "result";
                        },
                        Duration.ofMillis(100),
                        "corr-timeout"
                )
        ).isInstanceOf(LLMTimeoutException.class);
    }

    @Test
    void testCapacityExhaustion() throws Exception {
        // Fill pool with blocking tasks
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    pool.execute(
                            () -> {
                                Thread.sleep(2000);
                                return "blocking";
                            },
                            Duration.ofSeconds(5),
                            "corr-block-" + idx
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

        Thread.sleep(100);  // Let tasks acquire permits

        // Next task should fail due to no permits
        assertThatThrownBy(() ->
                pool.execute(
                        () -> "test",
                        Duration.ofMillis(100),
                        "corr-exceed"
                )
        ).isInstanceOf(LLMCapacityException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    void testExecutorShutdown() throws Exception {
        pool.execute(() -> "test", Duration.ofSeconds(5), "corr-1");
        pool.shutdown();
        // Should not throw, executor should be terminated gracefully
    }
}
