# backend/scripts/test-automation/runner/reset.py
"""
dev 테스트 계정(test%@again.com)의 세션·메시지·리포트 일괄 삭제.
POST /api/admin/test/reset — @Profile("dev") 전용 엔드포인트 호출.
"""
import asyncio
import aiohttp
from config import DEV_URL

# prod URL 실수 방지: DEV_URL에 "dev." 또는 "localhost"가 포함돼야 함
assert "dev." in DEV_URL or "localhost" in DEV_URL, \
    f"prod URL 사용 금지: {DEV_URL}"


async def reset_dev_test_data() -> dict:
    url = f"{DEV_URL}/api/admin/test/reset"
    print(f"[reset] POST {url}")
    async with aiohttp.ClientSession() as http:
        # /api/admin/test/reset은 인증 필요 — test1 계정 토큰으로 호출
        from runner.auth import AuthClient
        auth = AuthClient(http)
        token = await auth.login("test1@again.com", "test123")
        headers = {"Authorization": f"Bearer {token}"}
        async with http.post(url, headers=headers) as resp:
            if resp.status != 200:
                text = await resp.text()
                raise RuntimeError(f"Reset failed: {resp.status} — {text}")
            result = await resp.json()
    print(
        f"[reset] 완료 — "
        f"users={result.get('users_checked', '?')}, "
        f"sessions={result.get('sessions', '?')}, "
        f"messages={result.get('messages', '?')}, "
        f"reports={result.get('reports', '?')}"
    )
    return result


if __name__ == "__main__":
    asyncio.run(reset_dev_test_data())
