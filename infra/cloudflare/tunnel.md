# Cloudflare Tunnel 설정 가이드

## 터널 라우팅 구조

```
dev.againspring.net  →  Cloudflare Tunnel  →  localhost:8090  (againspring-nginx-dev)
againspring.net      →  Cloudflare Tunnel  →  localhost:8091  (againspring-nginx-prod)
www.againspring.net  →  Cloudflare Tunnel  →  localhost:8091  (againspring-nginx-prod)
```

## 설치 및 인증

```bash
# cloudflared 설치 (Ubuntu/Debian)
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 \
  -o /usr/local/bin/cloudflared
chmod +x /usr/local/bin/cloudflared

# Cloudflare 계정 인증
cloudflared tunnel login

# 터널 생성
cloudflared tunnel create againspring
```

## config.yml 작성

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

```bash
cloudflared tunnel route dns againspring dev.againspring.net
cloudflared tunnel route dns againspring againspring.net
cloudflared tunnel route dns againspring www.againspring.net
```

## 서비스 등록 (자동 시작)

```bash
cloudflared service install
systemctl enable cloudflared
systemctl start cloudflared
```

## 상태 확인

```bash
cloudflared tunnel info againspring
systemctl status cloudflared
