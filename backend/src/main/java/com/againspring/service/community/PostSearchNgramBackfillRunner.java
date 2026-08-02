package com.againspring.service.community;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 시작 시 ngram 미적재 게시글을 배치 백필.
 * {@code againspring.search.ngram-backfill-on-startup:false} 로 끌 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "againspring.search.ngram-backfill-on-startup", havingValue = "true", matchIfMissing = true)
public class PostSearchNgramBackfillRunner implements ApplicationRunner {

    private static final int BATCH = 200;
    private static final int MAX_BATCHES = 100; // 최대 20_000건/기동

    private final JdbcTemplate jdbcTemplate;
    private final PostSearchNgramIndexer indexer;

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT p.id AS id, p.title AS title, p.body_published AS body
                    FROM posts p
                    LEFT JOIN post_search_ngrams n ON n.post_id = p.id
                    WHERE n.post_id IS NULL
                      AND p.deleted_at IS NULL
                    LIMIT ?
                    """, BATCH);
            if (rows.isEmpty()) break;
            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                String title = (String) row.get("title");
                String body = (String) row.get("body");
                try {
                    indexer.reindex(id, title, body);
                    total++;
                } catch (Exception e) {
                    log.warn("[search-ngram] backfill failed for {}: {}", id, e.getMessage());
                }
            }
            if (rows.size() < BATCH) break;
        }
        if (total > 0) {
            log.info("[search-ngram] backfilled {} posts", total);
        }
    }
}
