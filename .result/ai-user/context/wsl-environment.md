# WSL 박스 환경 사실 (탐사 2026-06-15)

## 하드웨어

| 항목 | 값 |
|---|---|
| GPU | NVIDIA GeForce RTX 3090, **24 GB VRAM** |
| GPU 드라이버 | 610.43.02 (CUDA max 13.3 지원) |
| 컨테이너 CUDA | **12.4** 가능 (forward-compat: 드라이버 610 > CUDA 12.4) |
| RAM | 27 GB total, ~9 GB available (swap 6/8 GB 사용 중 — 압박 있음) |
| 디스크 | **685 GB 여유** / 1007 GB (29% 사용) |

## GPU 패스스루

- `/etc/docker/daemon.json`에 nvidia runtime 등록
- CDI `nvidia.com/gpu=all` 활성화
- 이미 GPU 컨테이너 다수 실행 중 → 패스스루 동작 확인

## 현재 GPU 사용 중인 컨테이너 (2026-06-15 기준)

| 컨테이너 | 모델 | VRAM |
|---|---|---|
| comfyui | LTX 비디오 생성 (WaggleBot) | 측정 불가 (WSL 제한) |
| fish-speech | TTS 모델 (WaggleBot) | 측정 불가 |
| 합계 추정 | — | ~13.4 GB (24 GB 중) |

**여유 VRAM**: ~11 GB (WaggleBot 상시 실행 시)

## VRAM 권한 (2026-06-15 ~ 22, 1주간)

- **WaggleBot 전체 unload 가능** → 24 GB 전체 사용 가능
- 이 기간에 Step 4 판별기 학습 실행 권장
- 이후: 11 GB 기준, 배치 크기 조정 필요

## 실행 중인 관련 프로젝트

- `~/Data/WaggleBot/` — LTX 비디오 + fish-speech TTS + ai_worker 등 (GPU)
- `~/Data/Again-Spring-Marketing/` — ASM, 포트 8200 (CPU only, anthropic API)
- **`~/Data/Again-Spring-AI-User/` — 신규, 포트 8201** (이 프로젝트)

## ASM 네트워킹 선례 (확인 완료)

```
AS 호스트 Tailscale IP: 100.81.189.92
WSL 박스 Tailscale IP: 100.115.252.61
ASM 포트: 8200
ASM DB: self-contained (asm-db mariadb:11, compose 내부)
ASM .env 패턴: DATABASE_URL + bearer tokens + anthropic API
```

## 주의 사항

- `nvidia-smi`는 PATH에 없음 → `/usr/lib/wsl/lib/nvidia-smi`
- swap 75% 사용 중 → RAM 사용량 최소화 (lazy loading, LRU eviction)
- 컨테이너별 VRAM 분리 측정 불가 (WSL 제한, `--query-compute-apps` N/A)
