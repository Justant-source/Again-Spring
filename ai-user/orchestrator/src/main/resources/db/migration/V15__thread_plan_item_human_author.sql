-- WP5 fix: human-reply budget must be per (post_id, human_user_id), not per post.
-- Without this column the budget seed query cannot tell whose conversation an existing
-- reply belongs to, so the first human on a post consumed the whole 3x5=15 budget and
-- every later human got zero AI replies (design §1.1-24: budgets are independent per human).
-- Loose ref only — no hard FK to backend-owned users/post_comments (V1 convention).

ALTER TABLE ai_thread_plan_items
    ADD COLUMN human_author_id VARCHAR(32) NULL AFTER target_comment_id;

-- Budget lookup: all human-reply items for one (post, human) conversation.
CREATE INDEX idx_thread_plan_item_post_human
    ON ai_thread_plan_items (target_post_id, human_author_id);
