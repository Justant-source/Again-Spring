#!/usr/bin/env python3
"""기존 dev DB에 이미 복사된 실사용자 posts/post_comments 원문을 1회 정리한다.
호스트가 'mariadb-dev'가 아니면 거부한다. --apply 없으면 dry-run.
사용: docker exec -i againspring-prod-dev-sync python /app/tools/scrub_dev_real_user_content.py [--apply]
(이미지에 tools가 없으면 호스트에서: cd ai-user/sync && DEV_DB_HOST=127.0.0.1 DEV_DB_PORT=3309 ... python3 ../tools/scrub_dev_real_user_content.py)
"""
import argparse
import os
import sys
from datetime import datetime, timezone

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "sync"))
import sync  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    host = sync.DEV["host"]
    if "mariadb-dev" not in host and host not in ("127.0.0.1", "localhost"):
        print(f"refusing: DEV_DB_HOST={host} is not a dev host", file=sys.stderr)
        return 2

    dev = sync.conn(sync.DEV)
    now = datetime.now(timezone.utc)
    try:
        with dev.cursor() as cur:
            cur.execute("SELECT id FROM users WHERE synthetic = 1")
            ctx = sync.SyncContext(frozenset(str(r["id"]) for r in cur.fetchall()))

            cur.execute("SELECT * FROM posts")
            posts = cur.fetchall()
            cur.execute("SELECT * FROM post_comments")
            comments = cur.fetchall()

            changed_posts = [(sync._mask_real_post(r, now, ctx), r) for r in posts]
            changed_posts = [(m, r) for m, r in changed_posts if m != r]
            changed_comments = [(sync._mask_real_comment(r, now, ctx), r) for r in comments]
            changed_comments = [(m, r) for m, r in changed_comments if m != r]
            print(f"posts to scrub: {len(changed_posts)} / {len(posts)}")
            print(f"comments to scrub: {len(changed_comments)} / {len(comments)}")

            if not args.apply:
                print("dry-run (pass --apply to write)")
                return 0

            for masked, orig in changed_posts:
                cols = [c for c in masked if masked[c] != orig[c]]
                cur.execute(
                    "UPDATE posts SET " + ", ".join(f"`{c}` = %s" for c in cols) + " WHERE id = %s",
                    [masked[c] for c in cols] + [orig["id"]],
                )
            for masked, orig in changed_comments:
                cur.execute("UPDATE post_comments SET body = %s WHERE id = %s", (masked["body"], orig["id"]))
            # PRIVATE/DRAFT/soft-deleted 실사용자 글은 dev에 남을 이유가 없다
            cur.execute(
                "DELETE p FROM posts p JOIN users u ON u.id = p.author_id "
                "WHERE u.synthetic = 0 AND (p.visibility = 'PRIVATE' OR p.status = 'DRAFT' OR p.deleted_at IS NOT NULL)"
            )
            print(f"private/draft/deleted real-user posts removed: {cur.rowcount}")
        dev.commit()
        print("applied")
        return 0
    except Exception:
        dev.rollback()
        raise
    finally:
        dev.close()


if __name__ == "__main__":
    sys.exit(main())
