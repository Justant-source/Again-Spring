# backend/scripts/test-automation/runner/auth.py
import aiohttp
from config import DEV_URL

class AuthClient:
    def __init__(self, session: aiohttp.ClientSession):
        self.session = session
        self.tokens: dict[str, str] = {}  # email → JWT

    async def login(self, email: str, password: str) -> str:
        async with self.session.post(
            f"{DEV_URL}/api/auth/login",
            json={"email": email, "password": password}
        ) as resp:
            data = await resp.json()
            # response: {"user": {...}, "token": {"accessToken": "...", ...}}
            token_obj = data.get("token") or {}
            token = (token_obj.get("accessToken") if isinstance(token_obj, dict)
                     else None) or data.get("accessToken")
            if not token:
                raise ValueError(f"Login failed for {email}: {data}")
            self.tokens[email] = token
            return token

    def get_token(self, email: str) -> str:
        return self.tokens.get(email, "")

    def auth_headers(self, email: str) -> dict:
        return {"Authorization": f"Bearer {self.get_token(email)}"}
