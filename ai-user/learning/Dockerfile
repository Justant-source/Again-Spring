FROM python:3.11-slim

WORKDIR /app

# 시스템 의존성 + Playwright 브라우저 의존성 (apt)
RUN apt-get update && apt-get install -y \
    gcc curl wget gnupg \
    libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
    libgbm1 libasound2 libpangocairo-1.0-0 libpango-1.0-0 libcairo2 \
    libatspi2.0-0 libx11-6 libx11-xcb1 libxcb1 libxext6 fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

# Python 패키지 (CPU-only torch)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Playwright Chromium 설치 (의존성은 apt로 이미 설치)
RUN playwright install chromium

# 앱 코드
COPY app/ ./app/

EXPOSE 8099

HEALTHCHECK --interval=30s --timeout=15s --start-period=180s \
  CMD curl -f http://localhost:8099/health || exit 1

# workers=1 필수: worker마다 lifespan→init_scheduler()가 떠서 daily crawl이 이중 실행되고
# (2026-08-11) 크롤 중 CPU 포화로 healthcheck 실패→ops-watchdog restart가 잡히던 원인.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8099", "--workers", "1"]
