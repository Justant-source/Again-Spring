#!/usr/bin/env python3
"""WO-RETIME-01 — 2026-07-31 새벽 압축배치로 한꺼번에 올라온 8개 글 재배치.

2026-07-30 밤 LEGACY 압축배치(및 그 직후 provider 재활성화)로 8개 AI 글이
03:06~03:53 KST와 12:19 KST에 몰려 생성됐다. 지금부터는 PLAN 모드 + ActivityCurve
+ ai_scheduled_posts 파이프라인이 실제로 동작하므로, 이 8개도 같은 파이프라인을
거친 것처럼 재배치한다:

  - 지정 슬롯이 이미 지난(현재 시각 이전) 글: created_at/댓글 created_at을
    delta-shift로 새 슬롯에 맞춰 당긴다(글 삭제·재생성 없음, 토큰 0).
  - 지정 슬롯이 아직 안 지난 글: 내용을 ai_scheduled_posts로 옮기고 원본
    글+댓글을 삭제한다. 슬롯 도래 시 ScheduledPostPublisher가 실제 글을
    새로 만들고 댓글 후보를 재생(replay)한다 — LLM 재호출 없음.

실사람 개입(좋아요 1건, 투표 1건)이 있는 글 2개는 반드시 "이미 지난 슬롯"으로만
배정한다(글을 삭제하는 미래-슬롯 경로로는 절대 보내지 않는다 — 실제 사람 행동
기록을 지울 수 없음). 대신 그 좋아요/투표 행의 시각도 글과 같은 delta만큼
같이 옮긴다(env/scripts/backdate-timeline.py가 이미 쓰는 것과 같은 기법).

기본은 dry-run(계획 SQL + 후보 JSON만 출력). --apply 시 실제 실행.
"""
import argparse
import json
import subprocess
import sys
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))
UTC = timezone.utc

DB_CONTAINER = "againspring-mariadb-prod"

# post_id -> 배정 슬롯(KST). 08:00~22:00 창에서 ActivityCurve.sampleFutureInstants(count=8,
# minSpacing=45min)로 뽑은 실제 값(2026-07-31 13:22 KST 실행) — 재실행해도 동일 배정 유지.
SLOT_ASSIGNMENTS_KST = {
    "post_6469083647d3484584a8": "2026-07-31T11:26:44",
    "post_d21666606f7747528fed": "2026-07-31T12:11:44",
    "post_b6d699cd78fa400ca03a": "2026-07-31T13:50:10",
    "post_4e37d6c6b8224f3dbb1a": "2026-07-31T19:00:00",
    "post_f6eb860c3d19437abb9b": "2026-07-31T19:45:00",
    "post_c94fabc948f54c40b584": "2026-07-31T20:30:00",
    "post_337976df1c32488889e0": "2026-07-31T21:15:00",
    "post_aa2d40f4aba5400eab52": "2026-07-31T22:00:00",
}

# 실사람 개입이 있어 반드시 과거-슬롯(delta-shift)로만 처리해야 하는 글.
MUST_BE_PAST_SLOT = {"post_6469083647d3484584a8", "post_d21666606f7747528fed"}


def db_query(db_name, db_user, db_pass, sql):
    result = subprocess.run(
        ["docker", "exec", DB_CONTAINER, "mariadb", f"-u{db_user}", f"-p{db_pass}",
         db_name, "-N", "-B", "-e", sql],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"[!] query failed: {result.stderr}", file=sys.stderr)
        sys.exit(1)
    rows = [line.split("\t") for line in result.stdout.splitlines() if line]
    return rows


def esc(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'") + "'"


def load_post(db_name, db_user, db_pass, post_id):
    rows = db_query(db_name, db_user, db_pass,
        f"SELECT id, author_id, category, title, body_raw, "
        f"UNIX_TIMESTAMP(created_at) FROM posts WHERE id={esc(post_id)}")
    if not rows:
        return None
    pid, author_id, category, title, body, created_ts = rows[0]
    return {"id": pid, "author_id": author_id, "category": category or "",
            "title": title or "", "body": body or "", "created_at": float(created_ts)}


def load_comments(db_name, db_user, db_pass, post_id):
    rows = db_query(db_name, db_user, db_pass,
        f"SELECT id, parent_comment_id, author_id, body, UNIX_TIMESTAMP(created_at) "
        f"FROM post_comments WHERE post_id={esc(post_id)} AND deleted_at IS NULL ORDER BY id")
    comments = []
    for cid, parent_id, author_id, body, created_ts in rows:
        comments.append({"id": cid, "parent_id": parent_id if parent_id != "NULL" else None,
                          "author_id": author_id, "body": body, "created_at": float(created_ts)})
    return comments


def build_candidates_json(comments):
    """persistResponse()가 기대하는 {"items":[{ref,parentRef,body,personaId}]} 형태로 변환."""
    ref_by_id = {c["id"]: f"c{c['id']}" for c in comments}
    items = []
    for c in comments:
        parent_ref = ref_by_id.get(c["parent_id"], "") if c["parent_id"] else ""
        items.append({"ref": ref_by_id[c["id"]], "parentRef": parent_ref,
                       "body": c["body"], "personaId": c["author_id"]})
    return {"items": items}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db-name", required=True)
    ap.add_argument("--db-user", required=True)
    ap.add_argument("--db-pass", required=True)
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--out-sql", default="/tmp/retime-posts.sql")
    args = ap.parse_args()

    now_utc = datetime.now(tz=UTC)
    sql_statements = ["START TRANSACTION;"]
    plan_report = []

    for post_id, slot_kst_str in SLOT_ASSIGNMENTS_KST.items():
        slot_kst = datetime.fromisoformat(slot_kst_str).replace(tzinfo=KST)
        slot_utc_ts = slot_kst.astimezone(UTC).timestamp()
        is_past = slot_utc_ts <= now_utc.timestamp()

        if post_id in MUST_BE_PAST_SLOT and not is_past:
            print(f"[!] {post_id}: 실사람 개입 글인데 배정 슬롯이 아직 미래 "
                  f"({slot_kst_str} KST) — 중단. 슬롯을 과거로 재배정할 것.", file=sys.stderr)
            sys.exit(1)

        post = load_post(args.db_name, args.db_user, args.db_pass, post_id)
        if post is None:
            print(f"[!] {post_id}: not found or already moved, skipping", file=sys.stderr)
            continue
        comments = load_comments(args.db_name, args.db_user, args.db_pass, post_id)

        if is_past:
            delta = round(slot_utc_ts - post["created_at"])
            plan_report.append({"post_id": post_id, "action": "BACKDATE", "slot_kst": slot_kst_str,
                                 "delta_seconds": round(delta), "comments": len(comments),
                                 "shifts_real_engagement": post_id in MUST_BE_PAST_SLOT})
            sql_statements.append(
                f"UPDATE posts SET created_at = created_at + INTERVAL {delta} SECOND, "
                f"updated_at = created_at + INTERVAL {delta} SECOND WHERE id={esc(post_id)};")
            sql_statements.append(
                f"UPDATE post_comments SET created_at = created_at + INTERVAL {delta} SECOND, "
                f"updated_at = created_at + INTERVAL {delta} SECOND WHERE post_id={esc(post_id)} "
                f"AND deleted_at IS NULL;")
            if post_id in MUST_BE_PAST_SLOT:
                # 실사람 좋아요/투표도 같은 delta만큼 이동 — 원본 backdate-timeline.py와 동일 기법.
                sql_statements.append(
                    f"UPDATE post_likes SET created_at = created_at + INTERVAL {delta} SECOND "
                    f"WHERE post_id={esc(post_id)};")
                sql_statements.append(
                    f"UPDATE votes SET created_at = created_at + INTERVAL {delta} SECOND "
                    f"WHERE post_id={esc(post_id)};")
        else:
            candidates = build_candidates_json(comments)
            candidates_json_sql = esc(json.dumps(candidates, ensure_ascii=False))
            plan_report.append({"post_id": post_id, "action": "HOLD_AND_REPUBLISH",
                                 "slot_kst": slot_kst_str, "comments": len(comments),
                                 "shifts_real_engagement": False})
            sql_statements.append(
                f"INSERT INTO ai_scheduled_posts "
                f"(id, persona_id, category, title, body, candidates_json, scheduled_publish_at, "
                f" status, origin, created_at, updated_at) VALUES "
                f"(UUID(), {esc(post['author_id'])}, {esc(post['category'])}, {esc(post['title'])}, "
                f" {esc(post['body'])}, {candidates_json_sql}, "
                f" '{slot_kst.astimezone(UTC).strftime('%Y-%m-%d %H:%M:%S')}', "
                f" 'SCHEDULED', 'RETIMED', UTC_TIMESTAMP(), UTC_TIMESTAMP());")
            sql_statements.append(
                f"DELETE FROM ai_thread_plan_items WHERE plan_id IN "
                f"(SELECT id FROM ai_thread_plans WHERE post_id={esc(post_id)});")
            sql_statements.append(f"DELETE FROM ai_thread_plans WHERE post_id={esc(post_id)};")
            sql_statements.append(f"DELETE FROM post_comments WHERE post_id={esc(post_id)};")
            sql_statements.append(f"DELETE FROM post_likes WHERE post_id={esc(post_id)};")
            sql_statements.append(f"DELETE FROM votes WHERE post_id={esc(post_id)};")
            sql_statements.append(f"DELETE FROM posts WHERE id={esc(post_id)};")

    sql_statements.append("COMMIT;")
    sql = "\n".join(sql_statements)
    with open(args.out_sql, "w", encoding="utf-8") as f:
        f.write(sql)

    print(json.dumps(plan_report, ensure_ascii=False, indent=2))
    print(f"\n[i] SQL 저장: {args.out_sql} ({len(sql_statements)}개 statement)")

    if args.apply:
        print("[i] --apply: 실제 DB에 적용합니다")
        result = subprocess.run(
            ["docker", "exec", "-i", DB_CONTAINER, "mariadb",
             f"-u{args.db_user}", f"-p{args.db_pass}", args.db_name],
            input=sql, capture_output=True, text=True,
        )
        if result.returncode != 0:
            print(f"[!] 적용 실패: {result.stderr}", file=sys.stderr)
            sys.exit(1)
        print("[i] 적용 완료")
    else:
        print("[i] dry-run — 실제 DB 변경 없음. --apply로 재실행 시 적용")


if __name__ == "__main__":
    main()
