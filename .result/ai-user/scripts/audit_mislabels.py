#!/usr/bin/env python3
"""
R1 — 오라벨 정밀 대조: example_bank (AS 8099) vs corpus_item (ML DB)

방법:
  1. AS 러닝 서비스 /examples/export에서 human/ai 전량 pull
  2. SHA-256 해시 인덱스 구축
  3. corpus_item label='ai' (NATEPAN·CLIEN) 각 항목 대조
     - human 인덱스 일치 → 오라벨 human → DELETE 후보
     - ai 인덱스 일치 or 무일치 → KEEP (과삭제 방지)
  4. ctx_* 테스트 누수 → 항상 DELETE 후보

Usage:
  python3 audit_mislabels.py            # dry-run (삭제 전 리포트만)
  python3 audit_mislabels.py --delete   # 실제 삭제 (사용자 승인 후 실행)

보존 제약 (절대 규칙):
  - 사용자 승인 전 --delete 실행 금지
  - 무일치 항목은 KEEP (증명된 human만 삭제)
"""
import hashlib, http.client, json, re, sys, os, subprocess, unicodedata
from collections import defaultdict

# ── 설정 ──────────────────────────────────────────────────────────────────
AS_HOST = os.environ.get("AS_LEARNING_HOST", "localhost")
AS_PORT = int(os.environ.get("AS_LEARNING_PORT", "8099"))

WSL_HOST = "100.115.252.61"
ML_DB_CONTAINER = "again-spring-ai-user-aiuser-ml-db-1"
ML_DB_USER = "root"
ML_DB_PASS = "aiuser_root_pw"
ML_DB_NAME = "aiuser_ml"


# ── 헬퍼 ──────────────────────────────────────────────────────────────────
def normalize(text: str) -> str:
    """공백·개행·유니코드 정규화 — 미세 변형 허용."""
    text = unicodedata.normalize("NFC", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def fetch_examples(source_class: str, limit: int = 20000) -> list:
    """AS 8099 /examples/export?sourceClass=<class>&limit=<n>"""
    conn = http.client.HTTPConnection(AS_HOST, AS_PORT, timeout=60)
    path = f"/examples/export?sourceClass={source_class}&limit={limit}"
    conn.request("GET", path)
    resp = conn.getresponse()
    body = resp.read().decode("utf-8")
    conn.close()
    if resp.status != 200:
        raise RuntimeError(f"GET {path} -> HTTP {resp.status}: {body[:200]}")
    data = json.loads(body)
    items = data if isinstance(data, list) else data.get("examples", data.get("items", []))
    print(f"  [{source_class}] {len(items)}건 수신", flush=True)
    return items


def wsl_db(sql: str) -> str:
    """WSL ML DB에서 SQL 실행 후 stdout 반환."""
    cmd = (
        f'ssh justant@{WSL_HOST} '
        f'"docker exec {ML_DB_CONTAINER} mariadb -u {ML_DB_USER} -p{ML_DB_PASS} {ML_DB_NAME} '
        f'--batch --skip-column-names -e \\"{sql.replace(chr(34), chr(92)+chr(34))}\\""'
    )
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"DB error: {r.stderr[:300]}")
    return r.stdout


# ── 메인 ──────────────────────────────────────────────────────────────────
def main():
    do_delete = "--delete" in sys.argv
    print("=" * 64)
    print("R1 — 오라벨 정밀 대조 (example_bank cross-reference)")
    print(f"mode: {'⚠️  DELETE' if do_delete else '🔍 DRY-RUN'}")
    print("=" * 64)

    # ── 1. example_bank 인덱스 ─────────────────────────────────────────
    print("\n[1/4] example_bank 인덱스 구축...")
    human_items = fetch_examples("human")
    ai_items    = fetch_examples("ai")

    human_exact: set = set()
    human_norm:  set = set()
    ai_exact:    set = set()
    ai_norm:     set = set()

    for item in human_items:
        text = (item.get("content") or "").strip()
        if text:
            human_exact.add(sha256_hex(text))
            human_norm.add(sha256_hex(normalize(text)))

    for item in ai_items:
        text = (item.get("content") or "").strip()
        if text:
            ai_exact.add(sha256_hex(text))
            ai_norm.add(sha256_hex(normalize(text)))

    print(f"  human 인덱스: exact={len(human_exact)}, normalized={len(human_norm)}")
    print(f"  ai    인덱스: exact={len(ai_exact)}, normalized={len(ai_norm)}")

    # ── 2. corpus_item label='ai' 조회 ───────────────────────────────
    print("\n[2/4] corpus_item label='ai' 조회 (NATEPAN·CLIEN)...")
    text_sql = (
        "SELECT id, community, source, content_hash, text "
        "FROM corpus_item "
        "WHERE label='ai' AND community IN ('NATEPAN','CLIEN');"
    )
    raw = wsl_db(text_sql)
    rows = [l for l in raw.strip().split("\n") if l.strip()]
    print(f"  조회 완료: {len(rows)}건")

    # ── 3. 해시 대조 ───────────────────────────────────────────────────
    print("\n[3/4] 해시 대조...")
    delete_ids:   dict = defaultdict(list)
    keep_ids:     dict = defaultdict(list)
    reason_count: dict = defaultdict(lambda: defaultdict(int))

    for line in rows:
        parts = line.split("\t", 4)
        if len(parts) < 5:
            continue
        item_id, community, source, content_hash, text = parts
        source = source.strip() if source.strip() not in ("NULL", "") else None
        text   = text.strip()

        # ctx_* A-B 테스트 누수 → 항상 DELETE
        if source and source.startswith("ctx_"):
            delete_ids[community].append(item_id)
            reason_count[community]["ctx_test_contamination"] += 1
            continue

        # 해시 대조 (exact 우선, normalized 폴백)
        norm_hash = sha256_hex(normalize(text))
        is_human_match = (content_hash in human_exact) or (norm_hash in human_norm)
        is_ai_match    = (content_hash in ai_exact)    or (norm_hash in ai_norm)

        if is_human_match and not is_ai_match:
            # 확실한 오라벨 human → DELETE
            delete_ids[community].append(item_id)
            reason_count[community]["human_match_delete"] += 1
        elif is_ai_match:
            # 진짜 AI (example_bank에서 확인) → KEEP
            keep_ids[community].append(item_id)
            reason_count[community]["ai_match_keep"] += 1
        else:
            # 무일치 → 보수적 KEEP (재구성/라이브 AI 추정)
            keep_ids[community].append(item_id)
            reason_count[community]["no_match_keep_conservative"] += 1

    # ── 4. 리포트 ─────────────────────────────────────────────────────
    print("\n[4/4] 결과 리포트")
    print("=" * 64)
    total_delete = sum(len(v) for v in delete_ids.values())
    total_keep   = sum(len(v) for v in keep_ids.values())

    for comm in sorted(reason_count.keys()):
        d = len(delete_ids[comm])
        k = len(keep_ids[comm])
        print(f"\n【{comm}】 DELETE={d} / KEEP={k} (총 {d+k})")
        for reason, cnt in sorted(reason_count[comm].items()):
            tag = "🗑️ " if "delete" in reason or "contamination" in reason else "✅ "
            print(f"  {tag}{reason}: {cnt}")

    print(f"\n──────────────────────────────────────")
    print(f"  총 DELETE 후보: {total_delete}")
    print(f"  총 KEEP:        {total_keep}")
    print(f"──────────────────────────────────────")

    # NATEPAN cond4 판정
    natepan_delete = len(delete_ids.get("NATEPAN", []))
    natepan_total  = natepan_delete + len(keep_ids.get("NATEPAN", []))
    if natepan_total > 0:
        delete_pct = natepan_delete / natepan_total * 100
        print(f"\nNATEPAN cond4 판정:")
        if delete_pct < 5:
            print(f"  오염분 {delete_pct:.1f}% → 미미 → cond4 PASS 유지 (재학습 불필요)")
        else:
            print(f"  오염분 {delete_pct:.1f}% → 유의미 → 재학습 후 cond4 재측정 필요 (provisional)")

    if total_delete == 0:
        print("\n✅ 삭제 대상 없음 — 코퍼스 이미 깨끗합니다.")
        return

    if not do_delete:
        # Dry-run: 샘플 표시
        print("\n🔍 DRY-RUN 완료. 실제 DELETE 전 사용자 승인 필요.")
        for comm in sorted(delete_ids.keys()):
            ids = delete_ids[comm]
            sample = ids[:5]
            more = f" ... ({len(ids)-5}건 더)" if len(ids) > 5 else ""
            print(f"  {comm} 삭제 예정 IDs: {sample}{more}")
        print("\n실제 삭제하려면: python3 audit_mislabels.py --delete")
        print("⚠️  사용자 승인 후에만 실행 (절대 규칙 #6)")
    else:
        # DELETE 실행
        print("\n⚠️  DELETE 실행 시작...")
        for comm in sorted(delete_ids.keys()):
            ids = delete_ids[comm]
            if not ids:
                continue
            id_list = ",".join(ids)
            del_sql = f"DELETE FROM corpus_item WHERE id IN ({id_list});"
            try:
                wsl_db(del_sql)
                print(f"  ✅ {comm}: {len(ids)}건 삭제 완료")
            except RuntimeError as e:
                print(f"  ❌ {comm}: 삭제 실패 — {e}")

        print("\n✅ DELETE 완료.")
        print("다음 단계: ML 서비스에서 /train 으로 재학습 권장")

        # 재학습 여부 판정
        if natepan_delete > 0:
            pct = natepan_delete / natepan_total * 100
            if pct >= 5:
                print(f"⚠️  NATEPAN {pct:.1f}% 삭제 → cond4 재측정 필요 (eval_run id=100 provisional 강등)")


if __name__ == "__main__":
    main()
