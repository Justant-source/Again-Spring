# Final independent verification — 01KZ837XTDDX2P2MG90N42BM53

> Scope: read-only verification of the completed ASM/WaggleBot job and its recovered artifacts. No deploy, restart, database mutation, publish, commit, or push was performed.
>
> Verdict: **PASS** — delivery, publication safety, media conformance, timing, fresh pp_v5 quality-gate evidence, and actual three-line visual accumulation all pass. An independent no-prompt ASR uses the alternate spelling `전혀 안먹히더라구요`; this is a non-blocking ASR/orthography variation, not evidence of a syllable split or liaison/pronunciation defect.

## 1. Terminal/publication state — PASS

Remote ASM read-only DB query for job `01KZ837XTDDX2P2MG90N42BM53` returned:

| Field | Result |
|---|---|
| status | `READY` |
| auto_publish | `0` / false |
| publication rows | `0` |
| post | WaggleBot `10022894` |

The authenticated ASM GET response independently returned `READY` and `publications: []`. No publication request was sent.

## 2. Remote current files and recovered artifacts — PASS

| Item | Remote source / recovered file | SHA-256 | Result |
|---|---|---|---|
| Canonical SD | ASM `/app/data/jobs/01KZ837XTDDX2P2MG90N42BM53/youtube_shorts__video.mp4` / `video.mp4` | `ba0fcc0c3ab481a80d4572964618bbde08b0193f68b85b1d173fe7503d38ed60` | exact match |
| Current FHD | WB `/home/justant/Data/WaggleBot/assets/media/videos/10022894_FHD.mp4` / `video-fhd.mp4` | `70897f12b69257f19e5756775b32f3b48aa475bbeef45d7b0ed0719f4d28c333` | exact match |

`ffprobe -count_frames` on the remote source files found identical stream properties, which apply to the byte-identical recovered artifacts:

| File | Video | Audio | A/V delta |
|---|---|---|---:|
| SD / `video.mp4` | H.264 Main, yuv420p, 1080x1920, 30/1 fps, 1,710 frames, 57.000000 s | AAC-LC, 44,100 Hz, mono, 56.991995 s | 8.005 ms |
| FHD / `video-fhd.mp4` | H.264 Main, yuv420p, 1080x1920, 30/1 fps, 1,710 frames, 57.000000 s | AAC-LC, 44,100 Hz, mono, 56.991995 s | 8.005 ms |

The 8.005 ms delta is below one 30-fps frame (33.333 ms). Dashboard-worker runtime evidence records `HD_RENDER` job `87` at `04:39:21Z`: `canonical SceneDirector preview remuxed ... -> 10022894_FHD.mp4`, then completed successfully. This is an atomic stream-remux path; it did not rebuild a divergent FHD timeline.

## 3. Scene, PCM, and frame timing — PASS for exercised cases

The final scene run was 16 scenes (13 body, 2 comments, 1 outro), 29 rendered frames, and 57.0 s. The renderer log records 26 aligned narrator lines with confidence `1.000`, `text visual lead=0.15s`, and the expected outro controls.

| Check | Independent evidence | Result |
|---|---|---|
| First visible text → first PCM speech | frame at 0.000 s already shows hook; final PCM first active sample is 0.150476 s | 150.476 ms lead; PASS |
| Sequential three-line accumulation | **All 29 timeline-entry end frames were extracted and inspected.** First run: 2.440 s shows `처음엔 한두 번만`; 3.720 s visibly retains it and adds `도와준 거였거든요`; 4.560 s visibly retains both and adds `근데 이제 뭐만 생기면` | PASS — actual renderer history crossed ScriptData-body boundaries and accumulated three text-only entries as designed |
| Final comment → outro visual hold | prior-comment PCM remains active through 53.246100 s; comment frame persists at 53.500 s; outro text frame is present at 53.555 s | 308.900 ms hold from actual speech end; PASS (>= 250 ms contract) |
| Outro text → speech / closing tail | outro text is visible by 53.555 s; PCM becomes active at 53.703878 s; final PCM active sample ends 56.360340 s, media ends 56.992449 s | 148.878 ms lead and 632.109 ms tail; PASS (>= 150 ms target within one sample/one frame capture tolerance; >= 500 ms tail) |

The production renderer also logged the deterministic timeline operation: `outro 전 0.25s 휴지`, measured native outro lead 0.095 s plus 0.055 s padding to 0.150 s, and `outro 후 0.50s tail`.

## 4. Fresh pp_v5 narrator and quality-gate runtime — PASS, with transcript condition

At `04:34:53Z` the worker began a fresh run for post `10022894`; unlike preceding cached runs, it has no `[TTS 캐시 히트]` entry. It issued three Fish Speech POSTs and generated the new narrator WAV at `04:36:14Z` (`335` source characters, `seg=3`, output `...cv_fd04d951.wav`). The cache identity implementation includes the explicit `pp_v5` suffix. The fresh render then reused this exact WAV for 26-line timing alignment.

The saved production gate audit reports the three actual request segments as accepted without retry:

| Semantic TTS request segment | Similarity | Word coverage | ASR confidence | Gate result |
|---|---:|---:|---:|---|
| 1 | 1.0000 | 1.0000 | 0.8581 | passed / no retry |
| 2 | 1.0000 | 1.0000 | 0.9094 | passed / no retry |
| 3 | 1.0000 | 1.0000 | 0.8218 | passed / no retry |

The target is in segment 3, whose expected source ends with `전혀 안 먹히더라고요.`. The production target-only audit reports similarity `1.0000`, coverage `1.0000`, confidence `0.5298`, passed/no retry. Its policy correctly did **not** enter the catastrophic-mismatch branch: all three segments passed normal thresholds and there is no retry log.

Independent whole-narrator, no-prompt faster-whisper transcription instead ended with **`전혀 안먹히더라구요`** (overall word confidence `0.8601`). Under the quality comparator's compact character method this spelling variant is `0.8889` similar to `전혀 안 먹히더라고요`; it is above the applicable short-text threshold and far above catastrophic conditions. It is an ASR spelling/spacing variation only: the source input is the requested phrase, the production target-only audit passes it at 1.0000, and neither check indicates the original failure mode (a split syllable, broken word boundary, or liaison mispronunciation). It is recorded for traceability but is non-blocking.

The supported SceneDirector fallback was also observed: the remote LLM returned HTTP 504, after which the rule-based fallback completed the 16-scene plan. This did not affect terminal success, media conformance, or timing.

## 5. Thumbnail state — PASS with documented ASM limitation

| Artifact | Measured state | Result |
|---|---|---|
| `thumbnail.jpg` | actual WaggleBot JPEG, 1280x720, RGB; SHA-256 `7c532b11742e2342e5fc73b78d311142002d3ad279788a36764003b251c804b3` | valid review thumbnail |
| `asm-thumbnail-fallback.png` | PNG, 1x1 gray+alpha; SHA-256 `c86dfc299551b0aa685264fecc72d06ab1b397a5f622bb84abdd875874546f8c` | fallback only; not a usable thumbnail |

ASM's package still names `youtube_shorts__thumbnail.png`; the retained 1x1 fallback must not be mistaken for the valid JPG.

## 6. Review evidence retained

All locally retained verification-only evidence is in `review/`:

- `narrator-fresh.wav` — fresh pp_v5 narrator PCM.
- `final-timeline-pcm.wav` — final mux timeline PCM.
- `target-phrase-segment-03.wav` — final narrator interval containing the target phrase.
- `comment-to-outro-transition.wav` — 52.8–56.992 s timing interval.
- `frame-0.000s.png`, `frame-2.320s.png`, `frame-3.610s.png` — initial lead and sequential accumulation.
- `scan-00-2.440s.png`, `scan-01-3.720s.png`, `scan-02-4.560s.png` — independently extracted 1 → 2 → 3 line accumulation proof; `scan-00` through `scan-28` are the complete 29-entry frame audit.
- `frame-53.500s.png`, `frame-53.555s.png`, `frame-53.700s.png`, `frame-56.450s.png` — comment/outro transition and closing hold.

No unrelated files were modified.
