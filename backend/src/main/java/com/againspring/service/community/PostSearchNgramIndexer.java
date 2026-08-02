package com.againspring.service.community;

import com.againspring.domain.community.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code post_search_ngrams} 유지. 게시글 제목/본문 변경 시 같은 트랜잭션에서 재색인.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchNgramIndexer {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void reindex(Post post) {
        if (post == null || post.getId() == null) return;
        reindex(post.getId(), post.getTitle(), post.getBodyPublished());
    }

    @Transactional
    public void reindex(String postId, String title, String bodyPublished) {
        Set<String> grams = PostSearchNgrams.extractForPost(title, bodyPublished);
        jdbcTemplate.update("DELETE FROM post_search_ngrams WHERE post_id = ?", postId);
        if (grams.isEmpty()) return;

        List<Object[]> batch = new ArrayList<>(grams.size());
        for (String gram : grams) {
            batch.add(new Object[]{postId, gram});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO post_search_ngrams (post_id, gram) VALUES (?, ?)",
                batch);
        log.debug("Reindexed search ngrams for post {} ({} grams)", postId, grams.size());
    }
}
