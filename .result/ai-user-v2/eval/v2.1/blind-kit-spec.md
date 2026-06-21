# Blind Kit Specification — AI-User v2.1 Evaluation

> **Version**: 2.1-Phase-1  
> **Updated**: 2026-06-21  
> **Purpose**: Define structure, sampling rules, and presentation format for blind humanness evaluation kits.

---

## 1. Kit Composition Template

**Unit of evaluation**: Account timeline (not individual posts)

### Structure:
```
Kit v2.1-Phase5-01:
├─ Account A (AI or Human, anonymized ID)
│  ├─ Post 1 (title + body)
│  ├─ Post 2
│  └─ Post 3
├─ Account B (AI or Human)
│  ├─ Post 1
│  ├─ Post 2
│  └─ Post 3
└─ [7 more accounts, random order]
```

**Kit size**: 10 accounts per kit
- ≥5 AI accounts (actual Again-Spring ai-user posts)
- ≥5 human accounts (real authors, confirmed human)
- **Randomized order** (no position pattern: 1st account is not always AI)

### Per-Account Requirements:

| Field | Requirement | Rationale |
|-------|-----------|-----------|
| **Post count** | ≥3 posts per account | Reveals timeline patterns, consistency, emotional arc |
| **Post date range** | Spread ≥1 week apart (or clustered realistically) | Mimics natural posting behavior |
| **Category mix** | At least 1 post per category (COUPLE, MARRIED, FRIEND, FAMILY, WORK, OTHER) across all accounts in kit | Broad humanness judgment across domains |
| **AI post source** | Phase 4+ generated posts (via ai-user service) | Ensures consistent generation pipeline |
| **Human post source** | NATEPAN real-author timeline (author_id present in example_bank) | Ground truth; controlled randomization |

---

## 2. AI Account Sampling Rules

**Source**: `ai-user/data/personas/*/history/` + orchestrator logs (2026-05-15 to 2026-06-21)

**Inclusion criteria**:
- Generated via Phase 4+ ai-user service (post 2026-05-15)
- Category matches kit topic (e.g., COUPLE AI post → COUPLE-themed kit)
- ≥1 follow-up comment from community (validates post wasn't rejected)
- No safety violations (already guardrailed; confirm ContentSafetyGuard passed)

**Exclusion**:
- Posts authored by owner or calibration staff (not blind)
- Posts already shown to evaluators in prior rounds
- Posts with metadata leaks (author name, timestamps outside post body)

---

## 3. Human Account Sampling Rules

**Source**: NATEPAN real-author example_bank (ref: `docs/shared/ai-user-v2/corpus-sources.md`)

**Inclusion criteria**:
- NATEPAN posts with confirmed `author_id` (e.g., "NATEPAN-661")
- ≥3 consecutive posts from same author (authentic timeline pattern)
- Category matches kit topic (COUPLE-related → COUPLE kit)
- Sentence length, tone variation, emotional authenticity passes sanity check (no auto-generated markers)
- No toxic/slur content (filter via `docs/shared/policies/forbidden-words.md`)

**Sampling method**:
- For each category (COUPLE, MARRIED, etc.), randomly select 1–2 NATEPAN authors
- Extract their 3–5 most recent posts chronologically
- Strip author name, timestamps → anonymize to "Human-XYZ" in kit

---

## 4. Randomization & Position Pattern Prevention

**Rule**: AI and human accounts must be **randomly interspersed** per kit.

**Process** (automated):
1. Pool: 5 AI accounts + 5 human accounts
2. Shuffle using cryptographic RNG (seed-free, non-deterministic)
3. Assign to positions 1–10
4. Verify: No runs of ≥3 consecutive AI or human (reroll if found)
5. Verify: No alternating pattern (A-H-A-H... reroll if found)

**Rationale**: v2 accidentally created odd=AI pattern → evaluators learned the position signal.

---

## 5. Answer Key & Blind Sealing

**Answer key** (locked, separate from kit):
```
Kit v2.1-Phase5-01-AnswerKey:
Account-1: AI (Persona Kiro, generated 2026-06-10)
Account-2: Human (NATEPAN-661, author: [REDACTED])
Account-3: AI (Persona Mia, generated 2026-06-05)
...
```

**Sealing procedure**:
1. Generate kit content (10 accounts + posts)
2. Generate answer key independently
3. Store answer key in encrypted/separate directory (not accessible to evaluators)
4. Distribute kit to evaluators
5. **Only after evaluators submit**: decrypt answer key, compute scores

**Implementation**:
- Kit file: `kit-v2.1-phase5-01.json`
- Answer key file: `.secrets/kit-v2.1-phase5-01-answers.json` (git-ignored)
- Python script: `blind-eval/seal-kit.py` (generates both, verifies no leaks)

---

## 6. Kit Format & Presentation

**JSON structure**:
```json
{
  "kit_id": "v2.1-phase5-01",
  "generated_at": "2026-06-21T10:00:00Z",
  "instruction": "[oracle-protocol.md § 2 instruction script]",
  "accounts": [
    {
      "id": "Account-1",
      "posts": [
        {
          "title": "제목 없음",
          "body": "실제 글 내용..."
        },
        ...
      ]
    },
    ...
  ]
}
```

**Display format** (to evaluator):
```
========================================
평가 키트 v2.1-Phase5-01
========================================

[oracle-protocol.md § 2 지시문 복사]

========================================
계정 1
========================================
글 1: [제목] + [본문]
글 2: ...
글 3: ...

========================================
계정 2
========================================
...
```

**Metadata exclusions**:
- ✗ Author name
- ✗ Original timestamp
- ✗ Platform signature (NATEPAN vs. Reddit, etc.)
- ✗ AI persona name ("Kiro", "Mia", etc.)
- ✓ Post body (only)
- ✓ Post title (if human authors naturally include)

---

## 7. Per-Account Evaluation Capture

**Evaluator response format**:
```
Account-1: [Human / AI] (optional: confidence 1–5)
Account-2: [Human / AI]
...
Account-10: [Human / AI]

Comments (optional): [Any reasoning or doubt notes]
```

**Analysis after collection** (≥3 evaluators):
- Compute per-evaluator score: (# correct AI calls) / 5
- Aggregate: simple average (default) or majority consensus if specified
- High-confidence accounts: ≥2/3 evaluators agree → flag separately
- Named-tell labels (optional): Why did evaluators disagree?
  - Example: "Account-3: 2/3 said AI due to 'formal tone', 1/3 said human"

---

## 8. Named-Tell Analysis (Post-Evaluation)

**Purpose**: Understand which linguistic/behavioral cues evaluators use to judge humanness.

**Process**:
1. For each account where ≥2 evaluators disagree (H vs. A):
   - Ask evaluators: "What made you choose that?"
   - Possible answers:
     - "Emotional inconsistency / too clean"
     - "Repetitive phrasing"
     - "Sudden tone shift"
     - "Too formal / too casual for a real person"
     - "Felt authentic; similar to real friends' posts"
2. Compile heuristics used by naive evaluators
3. Cross-reference with linguistic analysis (optional, not required)

**Deliverable**: `named-tell-labels-v2.1-phase5.md`
- Document which surface-level cues drove humanness judgments
- Feed back to model team for next iteration (if applicable)

---

## 9. Kit Rotation (Evaluator Tracking)

**Registry location**: `evaluator-registry.md`

**Tracking rule**:
- Each kit generation records: `[Evaluator-ID | Role | Kit-ID | Date | Category]`
- Before assigning evaluator to kit: check registry
  - If `Evaluator-ID` appears in `Kit-ID` row → **skip, use different evaluator**

**Example**:
| Evaluator | Role | Round | Kit ID | Date | Category |
|-----------|------|-------|--------|------|----------|
| E-001 | Naive | 5 | v2.1-phase5-01 | 2026-06-22 | COUPLE |
| E-002 | Naive | 5 | v2.1-phase5-01 | 2026-06-22 | COUPLE |
| E-003 | Naive | 5 | v2.1-phase5-01 | 2026-06-23 | COUPLE |
| Owner | Calibration | 5 | v2.1-phase5-01 | 2026-06-24 | COUPLE |

---

## 10. Phase 5 Execution Checklist

Before kit generation:

- [ ] NATEPAN example_bank filtered for 3+ consecutive posts per author
- [ ] AI account pool (Phase 4+, ≥5 posts min) extracted from ai-user logs
- [ ] Category distribution verified (each kit has COUPLE + MARRIED + etc.)
- [ ] Randomization script tested (no position patterns)
- [ ] Answer key sealing script verified (no leaks)
- [ ] Evaluation form template prepared (JSON + markdown)
- [ ] Evaluator pool ≥3 identities recruited (sealed contact list)
- [ ] oracle-protocol.md instruction script proofread for bias
- [ ] Named-tell label template prepared

---

## 11. Categories (광장 Topics)

Kits are organized by primary category to ensure semantic relevance:

- **COUPLE**: 연인 관계 (dating, romantic conflicts)
- **MARRIED**: 부부/결혼 (marriage, spouse issues)
- **FRIEND**: 친구 (friendship drama)
- **FAMILY**: 가족 (parents, siblings, relatives)
- **WORK**: 직장 (workplace, boss, colleagues)
- **OTHER**: 기타 (general life, mixed topics)

Each Phase 5 kit focuses on ≥1 category to avoid semantic mismatch (COUPLE kit should not mix FAMILY posts).

---

## References

- `oracle-protocol.md` — Evaluator profile, instruction, gate criterion
- `evaluator-registry.md` — Rotation tracking
- `docs/shared/ai-user-v2/corpus-sources.md` — NATEPAN, example_bank structure
- `docs/shared/policies/forbidden-words.md` — Safety filters
