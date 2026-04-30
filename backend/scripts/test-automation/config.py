# backend/scripts/test-automation/config.py

DEV_URL = "https://dev.againspring.net"
MAX_LLM_CALLS = 500          # 비용 안전장치
MAX_CONCURRENT = 5           # asyncio.Semaphore — dev BE 부하 보호
DEFAULT_WAIT_AFTER_SEND = 25  # 초 — LLM 응답 기다리는 최대 시간 (동시 부하 고려)

# 안전 검사: prod URL 실수로 사용 방지
ALLOWED_URLS = {"https://dev.againspring.net", "http://localhost:8090"}
assert DEV_URL in ALLOWED_URLS, f"prod URL 사용 금지: {DEV_URL}"
