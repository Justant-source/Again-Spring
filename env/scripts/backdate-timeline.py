#!/usr/bin/env python3
"""WO-BACKDATE-01 — prod 피드 타임라인 재배치.

131개 노출 게시글(및 딸린 comments/likes/votes/views)의 시각을
2026-06-06 ~ 2026-07-29(KST) 창에 재배치한다. 콘텐츠 생성/삭제 없음 — 시각만
delta-shift. 상세: .request/WO-BACKDATE-01_prod_피드_타임라인_재배치_계획.md

알고리즘:
  1. 요일가중 + 시간대 곡선으로 텍스처용 슬롯 시각 131개를 생성한다(매일 1개 이상
     시도). 이 단계는 아직 span 제약을 모른다.
  2. 슬롯을 오름차순, 게시글을 span 내림차순으로 정렬해 그대로 짝짓는다 —
     "가장 이른 슬롯에 가장 긴 span"을 배정하는 이 정렬-대-정렬 페어링은
     주어진 슬롯 시각 멀티셋에 대해 항상 최적(실행 가능하면 반드시 찾아내는)
     배정이다. 실측(2026-07-30): soft-delete 정화 이후 살아남은 131건은 span이
     최소 34h로 치우쳐 있어 최근 슬롯에 놓을 수 있는 "짧은 span" 글이 극소수다.
  3. 인접 슬롯 쌍을 반복적으로 훑으며, 두 배정 모두 여전히 실행 가능한 한도
     내에서 "참여도 낮은 글이 최신 슬롯에 오도록" 스왑한다(D-5 목적 반영).

기본은 dry-run(계획 SQL + 검증 리포트만 출력). --apply 시 실제 UPDATE 실행.
"""
import argparse
import json
import random
import subprocess
import sys
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))
UTC = timezone.utc

WINDOW_START_KST = datetime(2026, 6, 6, 0, 0, 0, tzinfo=KST)
_FIXED_END_KST = datetime(2026, 7, 29, 23, 59, 59, tzinfo=KST)
SEED = 20260730

HOUR_BUCKETS = [
    (0, 2, 0.04), (2, 6, 0.02), (6, 9, 0.07), (9, 12, 0.14),
    (12, 14, 0.16), (14, 17, 0.13), (17, 20, 0.15), (20, 23, 0.22), (23, 24, 0.07),
]

DB_CONTAINER = "againspring-mariadb-prod"
DB_NAME = "againspring_prod"


def load_posts(tsv_path):
    posts = []
    with open(tsv_path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 10:
                continue
            post_id, created_at, updated_at, vote_close_at, partner_answered_at, \
                last_activity, likes, comments, votes, view_count = parts[:10]
            posts.append({
                "id": post_id,
                "created_at": float(created_at),
                "updated_at": float(updated_at),
                "vote_close_at": float(vote_close_at) if vote_close_at else None,
                "partner_answered_at": float(partner_answered_at) if partner_answered_at else None,
                "last_activity": float(last_activity),
                "likes": int(likes),
                "comments": int(comments),
                "votes": int(votes),
                "view_count": int(view_count),
            })
    for p in posts:
        p["span"] = max(0.0, p["last_activity"] - p["created_at"])
        p["engagement"] = 4.0 * p["likes"] + 3.0 * p["comments"] + 2.5 * p["votes"] + 0.2 * p["view_count"]
    return posts


def day_range(window_end_kst, window_start_kst=WINDOW_START_KST):
    days = []
    d = window_start_kst
    while d.date() <= window_end_kst.date():
        days.append(d)
        d += timedelta(days=1)
    return days


def weekday_weight(dt_kst):
    return 1.25 if dt_kst.weekday() >= 5 else 0.95  # Sat=5, Sun=6


def generate_slot_times(n, rng, window_end_kst, window_start_kst=WINDOW_START_KST):
    """텍스처용 슬롯 시각 n개 생성: 요일 가중(매일 1개 우선) + 시간대 곡선.

    아직 span 제약을 모르는 단계 — 순수하게 "사람 커뮤니티다운 질감"만 만든다.
    실행 가능성은 이후 페어링 단계에서 검증한다.
    """
    days = day_range(window_end_kst, window_start_kst)
    weights = [weekday_weight(d) for d in days]
    total_w = sum(weights)

    if n >= len(days):
        base_count = {d.date(): 1 for d in days}
        remaining = n - len(days)
    else:
        # 공급(n)이 날짜 수보다 적다 — 가중치가 높은 날짜부터 우선 1개씩 배정하고
        # 나머지 날짜는 빈 날로 남긴다(§6 완화: 재배치 대상 자체가 부족한 경우).
        order = sorted(range(len(days)), key=lambda i: weights[i], reverse=True)
        base_count = {d.date(): 0 for d in days}
        for i in order[:n]:
            base_count[days[i].date()] = 1
        remaining = 0

    for _ in range(remaining):
        r = rng.random() * total_w
        acc = 0.0
        for d, w in zip(days, weights):
            acc += w
            if r <= acc:
                base_count[d.date()] += 1
                break
    assert sum(base_count.values()) == n

    slots = []
    for d in days:
        for _ in range(base_count[d.date()]):
            r = rng.random()
            acc = 0.0
            chosen = HOUR_BUCKETS[-1]
            for h0, h1, w in HOUR_BUCKETS:
                acc += w
                if r <= acc:
                    chosen = (h0, h1, w)
                    break
            h0, h1, _ = chosen
            sec_in_bucket = rng.uniform(0, (h1 - h0) * 3600)
            slot_dt = d + timedelta(hours=h0) + timedelta(seconds=sec_in_bucket)
            if slot_dt > window_end_kst:
                slot_dt = window_end_kst - timedelta(seconds=rng.uniform(0, 3600))
            slots.append(slot_dt)
    slots.sort()
    return slots


def sorted_pairing_assign(posts, slots, now_utc_ts):
    """슬롯 오름차순 <-> 게시글 span 내림차순 페어링 — 실행 가능하면 항상 찾아내는 배정."""
    slots_asc = sorted(slots)  # 이른 시각 -> 늦은 시각
    posts_desc = sorted(posts, key=lambda p: (-p["span"], p["id"]))  # 큰 span 먼저
    pairs = list(zip(slots_asc, posts_desc))

    violations = []
    for slot_dt, post in pairs:
        slot_ts = slot_dt.astimezone(UTC).timestamp()
        available = now_utc_ts - slot_ts
        if post["span"] > available:
            violations.append((post["id"], post["span"], slot_dt, available))
    return pairs, violations


def refine_by_engagement(pairs, now_utc_ts, max_passes=6):
    """인접 슬롯 쌍을 반복 스왑 — 실행 가능성 유지하며 참여도 낮은 글이 최신 슬롯에 오도록."""
    pairs = list(pairs)
    n = len(pairs)
    for _ in range(max_passes):
        changed = False
        for i in range(n - 1):
            slot_a, post_a = pairs[i]
            slot_b, post_b = pairs[i + 1]
            # slot_b가 slot_a보다 늦다(최신) — post_b가 post_a보다 참여도 낮아야 목적에 부합
            if post_b["engagement"] > post_a["engagement"]:
                # 스왑 후에도 둘 다 실행 가능해야 교환
                avail_a = now_utc_ts - slot_a.astimezone(UTC).timestamp()
                avail_b = now_utc_ts - slot_b.astimezone(UTC).timestamp()
                if post_b["span"] <= avail_a and post_a["span"] <= avail_b:
                    pairs[i], pairs[i + 1] = (slot_a, post_b), (slot_b, post_a)
                    changed = True
        if not changed:
            break
    return pairs


def build_updates(pairs):
    deltas = {}
    for slot_dt, post in pairs:
        new_ts = slot_dt.astimezone(UTC).timestamp()
        delta = new_ts - post["created_at"]
        deltas[post["id"]] = {
            "old_created_at": post["created_at"],
            "new_created_at": new_ts,
            "delta_seconds": delta,
            "span": post["span"],
            "engagement": round(post["engagement"], 2),
        }
    return deltas


def emit_sql(deltas):
    lines = ["START TRANSACTION;"]
    for post_id, d in deltas.items():
        delta = d["delta_seconds"]
        lines.append(
            f"UPDATE posts SET created_at = created_at + INTERVAL {delta} SECOND, "
            f"updated_at = updated_at + INTERVAL {delta} SECOND, "
            f"vote_close_at = vote_close_at + INTERVAL {delta} SECOND, "
            f"partner_answered_at = partner_answered_at + INTERVAL {delta} SECOND "
            f"WHERE id = '{post_id}';"
        )
        lines.append(
            f"UPDATE post_comments SET created_at = created_at + INTERVAL {delta} SECOND, "
            f"updated_at = updated_at + INTERVAL {delta} SECOND WHERE post_id = '{post_id}';"
        )
        lines.append(
            f"UPDATE post_likes SET created_at = created_at + INTERVAL {delta} SECOND "
            f"WHERE post_id = '{post_id}' OR comment_id IN "
            f"(SELECT id FROM post_comments WHERE post_id = '{post_id}');"
        )
        lines.append(
            f"UPDATE votes SET created_at = created_at + INTERVAL {delta} SECOND WHERE post_id = '{post_id}';"
        )
        lines.append(
            f"UPDATE post_views SET viewed_at = viewed_at + INTERVAL {delta} SECOND "
            f"WHERE post_id = '{post_id}' COLLATE utf8mb4_unicode_ci;"
        )
    lines.append("COMMIT;")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--posts-tsv", required=True)
    ap.add_argument("--tail-buffer-hours", type=float, default=36.0,
                     help="창 끝을 now - 이 값(시간)으로 캡핑해 tail 실행가능성 여유 확보")
    ap.add_argument("--span-cutoff-days", type=float, default=14.0,
                     help="이 값을 넘는 span의 글은 원래 자리에 유지(재배치 대상에서 제외)")
    ap.add_argument("--ext-window-start", default="2026-06-28",
                     help="재배치 대상(짧은 span)을 채울 확장 윈도우 시작일(KST, YYYY-MM-DD)")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--out-sql", default="/tmp/backdate.sql")
    ap.add_argument("--out-mapping", default="/tmp/backdate-mapping.json")
    args = ap.parse_args()

    all_posts = load_posts(args.posts_tsv)
    print(f"[i] 전체 대상 게시글 {len(all_posts)}건")

    now_utc_ts = datetime.now(tz=UTC).timestamp()
    now_kst = datetime.now(tz=UTC).astimezone(KST)

    cutoff_sec = args.span_cutoff_days * 86400
    stationary = [p for p in all_posts if p["span"] > cutoff_sec]
    movable = [p for p in all_posts if p["span"] <= cutoff_sec]
    print(f"[i] 장기 span(> {args.span_cutoff_days}일) {len(stationary)}건 — 원래 자리 유지(변경 없음)")
    print(f"[i] 단기 span(<= {args.span_cutoff_days}일) {len(movable)}건 — 확장 윈도우로 재배치")

    y, m, d = map(int, args.ext_window_start.split("-"))
    ext_window_start_kst = datetime(y, m, d, 0, 0, 0, tzinfo=KST)
    window_end_kst = min(_FIXED_END_KST, now_kst - timedelta(hours=args.tail_buffer_hours))
    print(f"[i] 확장 윈도우: {ext_window_start_kst} ~ {window_end_kst}")

    n = len(movable)
    spans_sorted = sorted(p["span"] for p in movable)
    print(f"[i] 재배치 대상 span 분포: min={spans_sorted[0]/3600:.1f}h "
          f"max={spans_sorted[-1]/86400:.1f}d median={spans_sorted[len(spans_sorted)//2]/3600:.1f}h")

    rng = random.Random(SEED)
    slots = generate_slot_times(n, rng, window_end_kst, ext_window_start_kst)

    pairs, violations = sorted_pairing_assign(movable, slots, now_utc_ts)
    if violations:
        print(f"[!] 정렬-페어링으로도 실행 불가능한 배정 {len(violations)}건 발견:", file=sys.stderr)
        for post_id, span, slot_dt, available in violations[:10]:
            print(f"    post={post_id} span={span/3600:.1f}h slot={slot_dt} "
                  f"available={available/3600:.1f}h", file=sys.stderr)
        print("[!] 이 텍스처(요일가중+시간대곡선)로는 실행 불가 — tail-buffer-hours를 늘리거나 "
              "창을 조정해야 함. 중단.", file=sys.stderr)
        sys.exit(2)
    print("[i] 정렬-페어링 실행가능성 검증 통과 (위반 0건)")

    pairs = refine_by_engagement(pairs, now_utc_ts)

    # 최종 검증: 스왑 이후에도 위반 없는지 재확인
    post_violations = 0
    for slot_dt, post in pairs:
        avail = now_utc_ts - slot_dt.astimezone(UTC).timestamp()
        if post["span"] > avail:
            post_violations += 1
    print(f"[i] 참여도 스왑 이후 위반: {post_violations}건 (기대: 0)")
    if post_violations > 0:
        print("[!] 스왑 로직 버그 — 중단", file=sys.stderr)
        sys.exit(1)

    # 리포트
    day_counts = {}
    for slot_dt, _ in pairs:
        d = slot_dt.date()
        day_counts[d] = day_counts.get(d, 0) + 1
    total_days = len(day_range(window_end_kst, ext_window_start_kst))
    empty_days = total_days - len(day_counts)
    print(f"[i] 창 {total_days}일 중 빈 날: {empty_days}일")
    print(f"[i] 일별 건수 범위: {min(day_counts.values())} ~ {max(day_counts.values())}")

    future_violations = sum(1 for slot_dt, _ in pairs
                             if slot_dt.astimezone(UTC).timestamp() > now_utc_ts)
    print(f"[i] 미래 시각 위반: {future_violations}건 (기대: 0)")

    deltas = build_updates(pairs)
    sql = emit_sql(deltas)
    with open(args.out_sql, "w", encoding="utf-8") as f:
        f.write(sql)
    with open(args.out_mapping, "w", encoding="utf-8") as f:
        json.dump(deltas, f, ensure_ascii=False, indent=2)
    print(f"[i] SQL 저장: {args.out_sql}")
    print(f"[i] 매핑(롤백용) 저장: {args.out_mapping}")

    if future_violations > 0:
        print("[!] 미래 시각 위반 발견 — 중단", file=sys.stderr)
        sys.exit(1)

    if args.apply:
        print("[i] --apply: 실제 DB에 적용합니다")
        result = subprocess.run(
            ["docker", "exec", "-i", DB_CONTAINER, "sh", "-c",
             f'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" {DB_NAME}'],
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
