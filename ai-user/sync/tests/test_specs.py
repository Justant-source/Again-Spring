from datetime import datetime, timezone

import sync

EXCLUDED = {"ai_user_runtime", "ai_user_generation_config", "system_setting", "ai_prompt_template"}


def test_config_tables_are_not_synced():
    names = {s.name for s in sync.SYNC_TABLES} | {s.name for s in sync.CONTENT_TABLES}
    assert not (names & EXCLUDED)


def test_posts_and_comments_have_mask_and_where():
    for specs in (sync.SYNC_TABLES, sync.CONTENT_TABLES):
        by = {s.name: s for s in specs}
        assert by["posts"].transform is sync._mask_real_post
        assert by["post_comments"].transform is sync._mask_real_comment
        assert "`deleted_at` IS NULL" in by["posts"].where
        assert "PRIVATE" in by["posts"].where and "DRAFT" in by["posts"].where
        assert by["post_comments"].where == "`deleted_at` IS NULL"


def test_build_select_sql_appends_where_only_when_columns_exist():
    spec = sync.TableSpec("posts", ("id",), time_columns=("updated_at",),
                          where="`visibility` <> 'PRIVATE' AND `deleted_at` IS NULL")
    since = datetime(2026, 9, 3, tzinfo=timezone.utc)
    sql, params = sync.build_select_sql("posts", ["id", "updated_at", "visibility", "deleted_at"], spec, since)
    assert "WHERE (`updated_at` >= %s) AND (`visibility` <> 'PRIVATE' AND `deleted_at` IS NULL)" in sql
    assert params == [since]
    sql2, _ = sync.build_select_sql("posts", ["id", "updated_at"], spec, since)
    assert "visibility" not in sql2  # 컬럼 없으면 where 생략


def test_full_mode_with_where():
    spec = sync.TableSpec("vote_options", ("id",), mode="full", where="`deleted_at` IS NULL")
    sql, params = sync.build_select_sql("vote_options", ["id", "deleted_at"], spec, datetime.now(timezone.utc))
    assert sql.endswith("WHERE (`deleted_at` IS NULL)") and params == []
