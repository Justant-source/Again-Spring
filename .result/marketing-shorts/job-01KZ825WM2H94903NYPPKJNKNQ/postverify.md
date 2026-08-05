# Post-render independent verification — 01KZ825WM2H94903NYPPKJNKNQ

검증 시각: 2026-08-05 13:22~13:30 KST  
대상: ASM job `01KZ825WM2H94903NYPPKJNKNQ`, WaggleBot post `10022894`  
범위: 읽기 전용 사후 검증. 배포·재시작·DB 변경·publish·commit·push는 수행하지 않았다.

## 최종 판정: **CONDITIONAL** (publish hold)

57초 ASM 쇼츠와 **13:29:57 KST에 교체된 최신 운영 FHD**의 발음·화면·A/V 싱크는 통과했다.
다만 ASM thumbnail은 1×1 fallback PNG이고, 병합 내레이션 전체를 다시 ASR한 품질 증거는 실패했다.
문제 구절 자체는 명확히 통과했지만 thumbnail 교체와 전체-ASR 결과의 triage 전에는
`READY`/`auto_publish=false`를 유지하고 publish하지 않는 조건부 판정이다.

## 1. 작업 결과

### 상태 및 동일성

- ASM DB: `READY`, `progress=1.0`, `auto_publish=false`, job attempts=1, error 없음,
  publication 없음. 대상은 `youtube_shorts` 하나다.
- ASM artifact `youtube_shorts__video.mp4`와 WaggleBot static render
  `assets/media/video/again_spring/..._SD.mp4`의 SHA-256은 모두
  `525916d68b80fb063879cff266632d82641cb32639503a89ae067e81d8fd1d59`이다.
  즉 ASM이 보관한 영상은 57초 static render의 정확한 복사본이다.
- WaggleBot `assets/media/videos/10022894_FHD.mp4`는 13:29:57 KST에 새 FHD
  (`321e2424…`, 재인코드이므로 SD/ASM artifact와 hash는 다름)로 교체됐다. 아래 최신 probe에서
  SD/ASM artifact와 동등한 57초 A/V 타임라인임을 확인했다.

### 내레이션 정규화·Fish 분할 (동일 운영 런타임)

원격 `env-ai_worker-1` 컨테이너에서 실제 `Content.get_script()` →
`ScriptData.to_narration_text()` → `normalize_for_tts()` → `fish_client._split_text()`를
실행했다. 최대 요청 길이는 150자이고, 정규화 후 333자로 정확히 3개 세그먼트가 나왔다.

| seg | 글자 수 | 실제 TTS 요청 텍스트 |
| --- | ---: | --- |
| 1 | 143 | 한 번 도와줬더니 오 년째 직장 엄마예요. / 처음엔 한두 번만 도와준 거였거든요. / 근데 이제 뭐만 생기면 저한테 달려와요. / 팀장 보고가 무섭다며 같이 가달라고 하고, / 발표자료 만들어놓고 컨펌도 해달라고, / 밥 혼자 먹기 뻘쭘하다고 같이 먹어달라 해요. |
| 2 | 128 | 아니 제가 엄마도 아닌데 이게 무슨 상황이에요. / 사실 피해 본 것도 한두 번이 아니에요. / 야근에 뒤처리까지 다 제가 했거든요. / 벌써 오 년째인데요 진급도 이 친구만 안 됐어요. / 직접 말할 거리도 없어서 그냥 무시해봤거든요. |
| 3 | 62 | 근데 무시했을 때 그 친구 반응이 가관이에요. / 눈치가 없는 건지 알고도 그러는 건지, / **전혀 안 먹히더라고요.** |

`/`는 보고서에서의 빈 줄 의미 블록 표기다. 원문 공백/빈 줄은 런타임 값 그대로 보존됐다.
문제 문구는 세그먼트 3에 정확히 한 번, 온전히 들어 있다. 두 실제 경계의
`validate_korean_segment_boundaries()` 결과는 `valid=true`, checked=2, unsafe=0이므로
한국어 어절 내부 경계는 없다.

### faster-whisper 발음 검증

같은 컨테이너의 lazy `small / cpu / int8` faster-whisper로 최신 narrator WAV
(`35.346009s`, 44.1kHz mono PCM)을 word timestamps로 다시 전사했다.

- 문제 구절(원본 WAV): 33.840–34.860s
  - `전혀` 33.840–34.340, probability **0.8930**
  - `안` 34.340–34.440, probability **0.6754**
  - `먹히더라고요.` 34.440–34.860, probability **0.8833**
- 인식 문구는 `전혀 안 먹히더라고요.`로 완전 일치한다. compact similarity=**1.000**,
  해당 문구 누락=없음이다.
- 해당 구간을 독립 청취할 수 있게 `problem-phrase-jeonhyeo-an-meokhideoragoyo.wav`를
  함께 저장했다(원 WAV 33.55–35.15s).

중요한 별도 경고: 전 대본을 한 번에 ASR한 결과는 mean word probability=0.6403,
compact similarity=**0.3612**, word coverage=**0.3452**, `failed/low_similarity`였다.
2.84–25.60s에서 ASR가 garble/누락됐고, 문제 문구 구간은 정상이다. 즉 타깃 발음의 직접 증거는
통과하지만, 현재 품질 게이트의 세그먼트별 성공이 전체 병합 내레이션의 의미 보존을 보장한다는
증거는 아니다.

### 장면/텍스트 lead 및 outro

`durations.json`(29 frames, 합계 56.992449s)과 실제 SD 프레임/PCM을 대조했다.

- 첫 화면은 t=0에 표시되고 merged PCM의 첫 유효 음절은 **0.150476s**였다.
- 본문 line transition은 다음 line을 음성 시작 **150ms** 전에 보여 준다. 예를 들어 실제 SD
  4.470s 프레임에는 `처음엔 한두 번만` → `도와준 거였거든요` →
  `근데 이제 뭐만 생기면`의 3줄이 순서대로 누적되어 있었고, 세 번째 음성 시작은 4.591995s다.
  최대 3줄 이후 다음 묶음에서 reset하는 renderer metadata도 확인했다.
- 문제 문구는 display 33.741995s, speech/frame start 33.891995s로 **150ms lead**다.
- 마지막 댓글의 마지막 loud PCM은 53.246167s, outro 화면 표시는 53.553605s로
  **307.438ms** hold(설계 250ms + 자연 tail)다. outro 첫 음절은 53.703877s로,
  outro text 뒤 **150.272ms**에 시작한다. 실제 53.600s SD 프레임에도 closing 문구와
  mascot/comment mockup이 확인됐다.

### MP4 메타데이터 및 A/V

| 파일 | video | audio | A/V delta | 판정 |
| --- | --- | --- | ---: | --- |
| ASM artifact = WaggleBot SD | H.264 Main, 1080×1920, 30fps, 1710 frames, 57.000000s | AAC-LC, 44.1kHz mono, 56.991995s | 8.005ms | PASS (30fps 1 frame=33.333ms 미만) |
| 최신 운영 `10022894_FHD.mp4` (13:29:57 KST) | H.264 Main, 1080×1920, 30fps, 1710 frames, 57.000000s | AAC-LC, 44.1kHz mono, 56.991995s | 8.005ms | PASS |

정상 merged TTS cache는 56.992449s이고 최신 FHD/SD/ASM artifact 모두 이 길이와 1 frame 이내로
일치한다. 참고로 검증 시작 시점의 이전 FHD(13:19:31 KST)는 37.600000s video / 36.799002s audio로
20.193447s가 잘리고 delta=800.998ms였으나, 최종 판정 전에 위 최신 FHD로 교체되어 현재 결함은
해소됐다.

### 로그, pp_v5 freshness, retry 및 alignment

- 현재 런타임 코드의 TTS cache version은 `pp_v5`다. 최종 재렌더 구간(04:16:07–04:19:18 UTC)은
  cache-hit가 아니라 Fish POST 3회(세 분할)로 새 narrator WAV를 생성했고, 최신 WAV mtime은
  13:17:21 KST다.
- 세 Fish 세그먼트(약 16.672s, 14.582s, 7.616s)에 대해 faster-whisper 처리 3회가 로그에 있다.
  추가 Fish POST/`TTS ASR 품질 의심`/품질 retry 로그가 없어 관측 가능한 ASR retry=0이다.
  하지만 성공 verdict 자체를 기록하지 않아, 세그먼트 gate가 실제로 어떤 similarity로 수용했는지는
  로그만으로 증명할 수 없다. 위 병합 WAV 재전사 실패 때문에 이 gate는 전체 내레이션 검증으로는
  **불충분** 판정이다.
- renderer alignment는 `26 lines`, confidence=**1.000**으로 기록됐고 실제 start list는
  마지막 문제 구절에 33.840s를 부여했다. 이는 화면 경계 계산에는 사용됐지만, 별도 ASR quality
  결과와의 불일치를 상쇄하지 않는다.
- Post `retry_count=22`는 이전 수동/재렌더 이력을 포함하는 DB 값이며 이번 ASM job attempts=1이나,
  최종 run 중 SceneDirector LLM 504가 1회 발생해 rule-based fallback으로 계속 진행했다.

### Thumbnail fallback 불일치

- ASM `youtube_shorts__thumbnail.png`는 68 bytes, PNG IHDR **1×1**(color type 4)이다.
- 같은 렌더의 Waggle thumbnail JPG는 52,168 bytes다.
- `upload.json`은 이 1×1 PNG를 thumbnail로 참조한다. 따라서 이는 유효한 Shorts thumbnail이 아닌
  fallback이며, 영상 본문과 별개로 publish 차단 사유다.

## 2. 수정 내용

- 코드/설정/원격 운영 데이터는 수정하지 않았다.
- 이 보고서와 문제 문구 WAV만 결과 폴더에 추가했다.

## 3. 테스트 결과물 위치

- [문제 문구 WAV](/home/justant/Data/Again-Spring/.result/marketing-shorts/job-01KZ825WM2H94903NYPPKJNKNQ/problem-phrase-jeonhyeo-an-meokhideoragoyo.wav)
- [이 사후 검증 보고서](/home/justant/Data/Again-Spring/.result/marketing-shorts/job-01KZ825WM2H94903NYPPKJNKNQ/postverify.md)

## 4. 수동 테스트 방법

1. ASM artifact `youtube_shorts__video.mp4`를 2.32s, 3.60s, 4.44s에서 확인해 본문 1→2→3줄
   누적과 각 음성보다 150ms 앞선 표시를 본다.
2. 33.74–34.86s에서 `전혀 안 먹히더라고요` 자막/발음을 듣고, 동봉 WAV도 별도로 청취한다.
3. 53.55s의 outro text, 약 53.70s의 첫 음절, 그리고 끝 tail을 확인한다.
4. publish 전에는 최신 FHD가 약 57.0s이며 audio/video delta가 33.333ms 이하인지 재확인하고,
   ASM thumbnail을 1×1 fallback이 아닌 실제 썸네일로 교체한 뒤 이미지 치수를 확인한다.

## 5. 추천 commit message

`docs: record job 01KZ825WM2H94903NYPPKJNKNQ post-render verification`

## 6. Doc-Sync

Doc-Sync: 없음 — 코드/문서 동작을 변경하지 않았고 결과 보고서·검증 WAV만 추가했다.
