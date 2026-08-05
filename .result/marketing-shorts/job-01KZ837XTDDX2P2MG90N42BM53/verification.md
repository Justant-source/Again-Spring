# Fresh production quality-gate verification

- ASM job: `01KZ837XTDDX2P2MG90N42BM53`
- State: `READY`; `auto_publish=false`; publication rows: `0` (not published).
- WaggleBot post: `10022894` (`RENDERED`); fresh `pp_v5` narrator TTS after precise narrator/scene-cache bypass.
- SceneDirector: the remote LLM returned HTTP 504, then the supported rule-based fallback completed the same 16-scene plan (13 body scenes, two comments, outro).
- Renderer: 29 frames, 57.0 s; narrator alignment 26 lines at confidence `1.000`.

## Fresh narrator ASR audit

The three exact semantic input segments were audited from the fresh narrator output with the production faster-whisper assessor. All results were `passed` with `requires_retry=false`:

| Segment | Similarity | Word coverage | ASR confidence |
|---|---:|---:|---:|
| 1 | 1.0000 | 1.0000 | 0.8581 |
| 2 | 1.0000 | 1.0000 | 0.9094 |
| 3 | 1.0000 | 1.0000 | 0.8218 |

Target phrase `전혀 안 먹히더라고요` was audited independently: similarity `1.0000`, word coverage `1.0000`, ASR confidence `0.5298`, `passed`, and no retry required. The fresh narrator transcript is complete.

## Video and artifacts

- `video.mp4`: ASM canonical preview artifact, SHA-256 `ba0fcc0c3ab481a80d4572964618bbde08b0193f68b85b1d173fe7503d38ed60`.
- `video-fhd.mp4`: automatic `HD_RENDER` job `87` used atomic stream remux from the completed canonical SceneDirector preview; SHA-256 `70897f12b69257f19e5756775b32f3b48aa475bbeef45d7b0ed0719f4d28c333`.
- Both source streams: H.264/AAC, 1080x1920, 30 fps, 1710 video frames; video `57.000000 s`, audio `56.991995 s`, A/V delta `8.005 ms`.
- `thumbnail.jpg`: actual WaggleBot JPEG, 1280x720.
- `asm-thumbnail-fallback.png`: retained only to document the ASM 1x1 fallback artifact.

## Delivery disposition

- This is the final verified delivery job for this iteration. The earlier
  `01KZ825WM2H94903NYPPKJNKNQ` run remains retained as superseded/conditional
  history and is not the release evidence.
- The canonical delivery file is `video.mp4` (SD); `video-fhd.mp4` is the
  corrected FHD derivative. The actual delivery thumbnail is `thumbnail.jpg`,
  not the ASM 1x1 fallback PNG.
- The fresh raw, post-process, and final narrator checks all passed. The
  `atempo` stage was investigated and rejected as the cause of the earlier FHD
  truncation. The confirmed cause was the legacy `HD_RENDER` ScriptData
  15-frame rebuild, which omitted comment scenes and the outro.
