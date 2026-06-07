package com.againspring.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * ERROR / WARN 이벤트를 메모리 링 버퍼에 보관하는 Logback appender.
 * 최대 MAX_SIZE 건을 유지하며 오래된 항목부터 제거.
 * 관리자 API에서 정적 접근자로 읽는다.
 */
public class LogBufferAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_SIZE = 500;

    private static final Deque<LogEntry> buffer = new ArrayDeque<>();

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
            synchronized (buffer) {
                if (buffer.size() >= MAX_SIZE) {
                    buffer.pollFirst();
                }
                buffer.addLast(new LogEntry(
                        Instant.ofEpochMilli(event.getTimeStamp()),
                        event.getLevel().toString(),
                        event.getLoggerName(),
                        event.getFormattedMessage(),
                        event.getThrowableProxy() != null
                                ? event.getThrowableProxy().getClassName() + ": " + event.getThrowableProxy().getMessage()
                                : null
                ));
            }
        }
    }

    public static List<LogEntry> getEntries(String level, int limit) {
        synchronized (buffer) {
            List<LogEntry> list = new ArrayList<>(buffer);
            if (level != null && !level.isBlank()) {
                String upper = level.toUpperCase();
                list.removeIf(e -> !e.level().equals(upper));
            }
            int from = Math.max(0, list.size() - limit);
            return new ArrayList<>(list.subList(from, list.size()));
        }
    }

    public record LogEntry(
            Instant timestamp,
            String level,
            String logger,
            String message,
            String exception
    ) {}
}
