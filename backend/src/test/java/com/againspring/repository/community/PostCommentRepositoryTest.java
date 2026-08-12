package com.againspring.repository.community;

import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostCommentRepository 공개 피드 필터 파생쿼리 검증 (JPA 슬라이스 / 실 MariaDB Testcontainers).
 *
 * Flyway 비활성 + Hibernate ddl-auto(create-drop)로 엔티티 기준 스키마를 생성한다.
 * (dev/prod 백엔드도 Flyway가 아닌 Hibernate ddl-auto로 스키마를 관리하므로 실제 환경과 동일한 경로.
 *  엔티티가 JSON·native enum·MEDIUMTEXT 등 MariaDB 전용 타입을 쓰므로 H2가 아닌 실 MariaDB 필요.)
 * 2026-06-07 버그: 공개 댓글 목록/카운트가 status·deleted_at를 무시 → 차단·삭제 댓글 노출.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("PostCommentRepository — 공개 피드 차단/삭제 필터")
class PostCommentRepositoryTest {

    @Container
    @SuppressWarnings({"resource", "rawtypes"})
    static final MariaDBContainer DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("againspring_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> DB.getJdbcUrl() + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        // Flyway(V66 등) 우회 — 실 환경처럼 Hibernate가 엔티티 기준으로 스키마 생성
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PostCommentRepository repo;

    private static final String POST_ID = "post_filter_001";
    private Long activeTopId;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        Instant t = Instant.parse("2026-06-07T00:00:00Z");
        activeTopId = save(null, "ACTIVE 최상위", CommentStatus.ACTIVE, null, t).getId();
        save(null, "BLOCKED 최상위", CommentStatus.BLOCKED, null, t.plusSeconds(1));
        save(null, "삭제된 최상위", CommentStatus.ACTIVE, t.plusSeconds(2), t.plusSeconds(2));
        save(activeTopId, "ACTIVE 답글", CommentStatus.ACTIVE, null, t.plusSeconds(3));
        save(activeTopId, "BLOCKED 답글", CommentStatus.BLOCKED, null, t.plusSeconds(4));
        save(activeTopId, "삭제된 답글", CommentStatus.ACTIVE, t.plusSeconds(5), t.plusSeconds(5));
    }

    private PostComment save(Long parentId, String body, CommentStatus status, Instant deletedAt, Instant createdAt) {
        return repo.save(PostComment.builder()
                .postId(POST_ID)
                .parentCommentId(parentId)
                .authorId("author-1")
                .body(body)
                .status(status)
                .deletedAt(deletedAt)
                .likeCount(0)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build());
    }

    @Test
    @DisplayName("최상위 필터 쿼리 — ACTIVE & deletedAt IS NULL만 반환 (BLOCKED·삭제 제외)")
    void topLevelFiltered_returnsOnlyVisible() {
        List<PostComment> result = repo
                .findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(POST_ID, CommentStatus.ACTIVE);
        assertThat(result).extracting(PostComment::getBody).containsExactly("ACTIVE 최상위");
    }

    @Test
    @DisplayName("답글 필터 쿼리 — ACTIVE & deletedAt IS NULL만 반환 (BLOCKED·삭제 제외)")
    void repliesFiltered_returnsOnlyVisible() {
        List<PostComment> result = repo
                .findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(activeTopId, CommentStatus.ACTIVE);
        assertThat(result).extracting(PostComment::getBody).containsExactly("ACTIVE 답글");
    }

    @Test
    @DisplayName("공개 피드 정렬 — 최상위·답글 모두 createdAt DESC(최신순)")
    void publicFeed_ordersNewestFirst() {
        Instant t = Instant.parse("2026-06-07T01:00:00Z");
        save(null, "오래된 최상위", CommentStatus.ACTIVE, null, t);
        save(null, "최신 최상위", CommentStatus.ACTIVE, null, t.plusSeconds(10));
        Long parent = save(null, "부모", CommentStatus.ACTIVE, null, t.plusSeconds(20)).getId();
        save(parent, "오래된 답글", CommentStatus.ACTIVE, null, t.plusSeconds(21));
        save(parent, "최신 답글", CommentStatus.ACTIVE, null, t.plusSeconds(22));

        assertThat(repo.findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        POST_ID, CommentStatus.ACTIVE))
                .extracting(PostComment::getBody)
                .startsWith("부모", "최신 최상위", "오래된 최상위");
        assertThat(repo.findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        parent, CommentStatus.ACTIVE))
                .extracting(PostComment::getBody)
                .containsExactly("최신 답글", "오래된 답글");
    }

    @Test
    @DisplayName("공개 댓글 수 — 차단·삭제 제외(visible=2), 전체 카운트(6)와 구분")
    void countFiltered_excludesBlockedAndDeleted() {
        assertThat(repo.countByPostIdAndStatusAndDeletedAtIsNull(POST_ID, CommentStatus.ACTIVE))
                .as("공개(ACTIVE & not-deleted) 댓글 수").isEqualTo(2);
        assertThat(repo.countVisibleByPostId(POST_ID, CommentStatus.ACTIVE))
                .as("목록 노출 가능 댓글 수(최상위+visible parent 답글)").isEqualTo(2);
        assertThat(repo.countByPostId(POST_ID))
                .as("전체 댓글 수(상태 무관)").isEqualTo(6);
    }

    @Test
    @DisplayName("visible count — soft-deleted/BLOCKED 부모 아래 ACTIVE 고아 대댓글 제외")
    void countVisible_excludesOrphanRepliesUnderHiddenParents() {
        Instant t = Instant.parse("2026-06-07T02:00:00Z");
        Long softDeletedParent = save(null, "soft-deleted 부모", CommentStatus.ACTIVE, t, t).getId();
        save(softDeletedParent, "고아 ACTIVE 답글(삭제부모)", CommentStatus.ACTIVE, null, t.plusSeconds(1));
        Long blockedParent = save(null, "BLOCKED 부모", CommentStatus.BLOCKED, null, t.plusSeconds(2)).getId();
        save(blockedParent, "고아 ACTIVE 답글(차단부모)", CommentStatus.ACTIVE, null, t.plusSeconds(3));

        // setUp 기본 visible 2 + 고아 답글 2 = status-only count 4, visible count는 여전히 2
        assertThat(repo.countByPostIdAndStatusAndDeletedAtIsNull(POST_ID, CommentStatus.ACTIVE))
                .as("status-only는 고아 ACTIVE 답글 포함").isEqualTo(4);
        assertThat(repo.countVisibleByPostId(POST_ID, CommentStatus.ACTIVE))
                .as("목록에 안 보이는 고아 답글 제외").isEqualTo(2);
    }

    @Test
    @DisplayName("visible count — depth≥2 중첩 대댓글은 UI에 안 보이므로 제외")
    void countVisible_excludesNestedDepth2PlusReplies() {
        Instant t = Instant.parse("2026-06-07T03:00:00Z");
        Long depth1 = save(activeTopId, "직계 대댓글", CommentStatus.ACTIVE, null, t).getId();
        save(depth1, "depth2 중첩", CommentStatus.ACTIVE, null, t.plusSeconds(1));
        save(depth1, "depth2 중첩2", CommentStatus.ACTIVE, null, t.plusSeconds(2));

        // setUp visible 2 + depth1 1 = 3. depth2 2건은 배지에 포함되면 안 됨
        assertThat(repo.countByPostIdAndStatusAndDeletedAtIsNull(POST_ID, CommentStatus.ACTIVE))
                .as("status-only는 depth2 포함").isEqualTo(5);
        assertThat(repo.countVisibleByPostId(POST_ID, CommentStatus.ACTIVE))
                .as("UI 2단만 카운트").isEqualTo(3);
    }
}
