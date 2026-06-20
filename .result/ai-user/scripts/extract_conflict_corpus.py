#!/usr/bin/env python3
"""
Extract conflict-type human posts from AS running service (localhost:8099).
Uses keyword matching only — no LLM.
"""

import urllib.request
import json
import sys
from collections import defaultdict

CONFLICT_KW = [
    '남자친구', '남친', '여친', '여자친구',
    '직장', '동료', '상사', '선배',
    '부모', '엄마', '아빠', '언니', '오빠',
    '남편', '아내',
    '갈등', '싸웠', '화났', '억울', '어이없', '황당', '배신',
    '친구가', '친구한테', '이 인간', '진짜 너무',
    '어떻게', '모르겠', '서운', '실망', '화가', '이해를',
    '왜 나만', '참을', '못하겠'
]

def fetch_all(source_class, limit=5000):
    """Fetch all human posts from AS example_bank."""
    items = []
    offset = 0
    print(f'[*] Fetching {source_class}...', file=sys.stderr)

    while True:
        url = f'http://localhost:8099/examples/export?sourceClass={source_class}&contentType=POST&limit=100&offset={offset}'
        try:
            req = urllib.request.Request(url)
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode('utf-8'))
            batch = data.get('items', [])
            items.extend(batch)
            print(f'  offset={offset}, batch_size={len(batch)}, total={len(items)}', file=sys.stderr)

            if len(batch) < 100:
                break
            offset += 100

            if len(items) >= limit:
                items = items[:limit]
                break

        except Exception as e:
            print(f'[!] Error at offset {offset}: {e}', file=sys.stderr)
            break

    return items

def is_conflict(text):
    """Check if text contains conflict keywords and is long enough."""
    if not text or len(text.strip()) < 60:
        return False
    return any(k in text for k in CONFLICT_KW)

def main():
    # Fetch all human posts
    human_items = fetch_all('human')
    print(f'\n[+] Total human posts fetched: {len(human_items)}', file=sys.stderr)

    if not human_items:
        print('[!] No human posts found', file=sys.stderr)
        return

    # Show sample structure
    if human_items:
        print(f'[*] Sample post keys: {list(human_items[0].keys())}', file=sys.stderr)

    # Filter for conflict posts
    conflict_posts = []
    source_counts = defaultdict(int)

    for item in human_items:
        content = item.get('content', '')
        if is_conflict(content):
            conflict_posts.append(item)
            source = item.get('source', 'UNKNOWN')
            source_counts[source] += 1

    print(f'\n[+] Conflict posts found: {len(conflict_posts)}/{len(human_items)} ({100*len(conflict_posts)/len(human_items):.1f}%)', file=sys.stderr)
    print(f'[+] Source distribution:', file=sys.stderr)
    for source, count in sorted(source_counts.items(), key=lambda x: -x[1]):
        print(f'    {source}: {count}', file=sys.stderr)

    # Show samples
    print(f'\n[*] Sample conflict posts:', file=sys.stderr)
    for i, post in enumerate(conflict_posts[:3]):
        content = post.get('content', '')
        source = post.get('source', 'UNKNOWN')
        print(f'  [{i+1}] [{source}] {repr(content[:80])}...', file=sys.stderr)

    # Output JSON
    output = {
        'metadata': {
            'total_human': len(human_items),
            'conflict_count': len(conflict_posts),
            'conflict_ratio': len(conflict_posts) / len(human_items) if human_items else 0,
            'source_distribution': dict(source_counts)
        },
        'posts': conflict_posts
    }

    with open('/home/justant/Data/Again-Spring/.result/ai-user/blind/conflict-human-corpus-draft.json', 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print(f'\n[✓] Results saved to conflict-human-corpus-draft.json', file=sys.stderr)

if __name__ == '__main__':
    main()
