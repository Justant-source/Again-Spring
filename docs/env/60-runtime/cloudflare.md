# Cloudflare Tunnel

호스트의 nginx 컨테이너(`8090`/`8091`)를 외부 도메인에 연결하는 라우팅 계층.

## 라우팅 구조

```
dev.againspring.net   →  Cloudflare Tunnel  →  localhost:8090  (againspring-nginx-dev)
againspring.net       →  Cloudflare Tunnel  →  localhost:8091  (againspring-nginx-prod)
www.againspring.net   →  Cloudflare Tunnel  →  localhost:8091  (againspring-nginx-prod)
```

prod nginx는 `set_real_ip_from` + `real_ip_header CF-Connecting-IP`로 클라이언트 실 IP를 복원.

## 설치 및 인증 (호스트, 1회)

### cloudflared 바이너리 설치

Ubuntu/Debian 기준:

```bash
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 \
  -o /usr/local/bin/cloudflared
chmod +x /usr/local/bin/cloudflared
```

### Cloudflare 계정 인증

```bash
# 브라우저 자동 열림 — Cloudflare 로그인 후 인증 완료
cloudflared tunnel login
```

### 터널 생성

```bash
cloudflared tunnel create againspring
```

`<TUNNEL_UUID>`를 확인하고 아래 config.yml에 입력.

## 설정 파일 작성

`~/.cloudflared/config.yml`:

```yaml
tunnel: <TUNNEL_UUID>
credentials-file: /root/.cloudflared/<TUNNEL_UUID>.json

ingress:
  - hostname: dev.againspring.net
    service: http://localhost:8090

  - hostname: againspring.net
    service: http://localhost:8091

  - hostname: www.againspring.net
    service: http://localhost:8091

  # fallback
  - service: http_status:404
```

## DNS 레코드 등록

한 줄씩 실행하거나 한 번에 실행:

```bash
cloudflared tunnel route dns againspring dev.againspring.net
cloudflared tunnel route dns againspring againspring.net
cloudflared tunnel route dns againspring www.againspring.net
```

## 서비스 등록 (자동 시작)

systemd에 등록해 호스트 부팅 시 자동 시작:

```bash
cloudflared service install
systemctl enable cloudflared
systemctl start cloudflared
```

## 상태 확인

```bash
# 터널 상태
cloudflared tunnel info againspring

# systemd 상태
systemctl status cloudflared

# 실시간 로그
journalctl -u cloudflared -f
```

## 운영 주의사항

### 컨테이너 재기동

- 컨테이너 재기동 시 Tunnel은 재시작 불필요
- 호스트 포트(`8090` / `8091`)가 동일하면 자동 연결 유지

### 새 서브도메인 추가

다음 순서로 진행:

1. `~/.cloudflared/config.yml` ingress에 새 항목 추가
2. `cloudflared tunnel route dns againspring <new-hostname>` 실행
3. `systemctl restart cloudflared`

### Cloudflare CIDR 업데이트

- prod nginx(`env/nginx/prod.conf`)에 Cloudflare IP 대역이 등록돼 있음
- 변경 시 [공식 IP 목록](https://www.cloudflare.com/ips-v4/)에 맞춰 업데이트
