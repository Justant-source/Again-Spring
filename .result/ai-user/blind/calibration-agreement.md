# Calibration Agreement Report

## Summary

Cross-era proxy bias analysis: R9-era Codex survey on R14 Claude-generated content.

| Survey | Community | Proxy Acc | Human Acc | Gap | N |
|---|---|---:|---:|---:|---:|
| r14-theqoo-seed1 | THEQOO | 35.0% | 84.2% | 49.2% | 19 |
| r14-theqoo-seed2 | THEQOO | 30.0% | 84.2% | 54.2% | 19 |
| r14-theqoo-seed3 | THEQOO | 25.0% | 84.2% | 59.2% | 19 |

## Detailed Results

### r14-theqoo-seed1 (THEQOO)

#### Confusion Matrix

| Outcome | Count |
|---|---:|
| Both Correct | 6 |
| Human Only (proxy missed) | 10 |
| Proxy Only (human missed) | 0 |
| Both Wrong | 3 |

#### Proxy Blind Spots (Human Caught AI)

Pairs: 2, 9, 10, 12, 13, 15, 16, 17, 18, 20

- Average judge score margin: 3.9
- Min margin: 0
- Max margin: 16

### r14-theqoo-seed2 (THEQOO)

#### Confusion Matrix

| Outcome | Count |
|---|---:|
| Both Correct | 5 |
| Human Only (proxy missed) | 11 |
| Proxy Only (human missed) | 0 |
| Both Wrong | 3 |

#### Proxy Blind Spots (Human Caught AI)

Pairs: 3, 9, 10, 11, 12, 13, 14, 16, 17, 18, 20

- Average judge score margin: 3.0
- Min margin: 0
- Max margin: 10

### r14-theqoo-seed3 (THEQOO)

#### Confusion Matrix

| Outcome | Count |
|---|---:|
| Both Correct | 4 |
| Human Only (proxy missed) | 12 |
| Proxy Only (human missed) | 0 |
| Both Wrong | 3 |

#### Proxy Blind Spots (Human Caught AI)

Pairs: 2, 3, 8, 9, 10, 12, 13, 15, 16, 17, 18, 20

- Average judge score margin: 4.2
- Min margin: 0
- Max margin: 15

## Calibration Note

**Important Caveat**: The `gap_hi` value (59.2%) is derived from an R9-era Codex survey applied to R14 Claude-generated content. This represents a **cross-era comparison** and may overestimate the true proxy bias on same-era content.

The gap reflects:
1. **Codex detector patterns** (R9-era training) vs. **Claude generation patterns** (R14-era)
2. Human expert knowledge of generation differences across model eras
3. Conservative upper bound for production calibration

**Recommendation**: Use `gap_hi` as a conservative overestimate. The true proxy accuracy on R14 content may be higher than this suggests.
