# backend/scripts/test-automation/runner/api_client.py
import aiohttp
from config import DEV_URL

class DasibomClient:
    """다시봄 API 클라이언트. typing 엔드포인트 없음 (V5 단순화)."""

    def __init__(self, session: aiohttp.ClientSession, auth_headers: dict):
        self.session = session
        self.headers = {**auth_headers, "Content-Type": "application/json"}

    async def create_session(self, relation_type: str = "couple",
                              category_data: dict = None) -> dict:
        payload = {"relationType": relation_type}
        if category_data:
            payload.update(category_data)
        async with self.session.post(
            f"{DEV_URL}/api/sessions", json=payload, headers=self.headers
        ) as resp:
            return await resp.json()

    async def send_message(self, session_id: str, content: str) -> dict:
        async with self.session.post(
            f"{DEV_URL}/api/sessions/{session_id}/messages",
            json={"content": content}, headers=self.headers
        ) as resp:
            return await resp.json()

    async def get_messages(self, session_id: str, since: int = 0) -> list:
        url = f"{DEV_URL}/api/sessions/{session_id}/messages"
        if since:
            url += f"?since={since}"
        async with self.session.get(url, headers=self.headers) as resp:
            return await resp.json()

    async def get_all_messages(self, session_id: str) -> list:
        return await self.get_messages(session_id)

    async def create_invite_token(self, session_id: str) -> str:
        async with self.session.post(
            f"{DEV_URL}/api/sessions/{session_id}/invite", headers=self.headers
        ) as resp:
            data = await resp.json()
            return data.get("token") or data.get("inviteToken", "")

    async def join_via_token(self, invite_token: str) -> dict:
        async with self.session.post(
            f"{DEV_URL}/api/sessions/join/{invite_token}",
            json={}, headers=self.headers
        ) as resp:
            return await resp.json()

    async def request_finalize(self, session_id: str) -> dict:
        async with self.session.post(
            f"{DEV_URL}/api/sessions/{session_id}/finalize", headers=self.headers
        ) as resp:
            return await resp.json()

    async def agree_finalize(self, session_id: str) -> dict:
        async with self.session.post(
            f"{DEV_URL}/api/sessions/{session_id}/finalize/agree", headers=self.headers
        ) as resp:
            return await resp.json()

    async def get_session_status(self, session_id: str) -> dict:
        async with self.session.get(
            f"{DEV_URL}/api/sessions/{session_id}", headers=self.headers
        ) as resp:
            return await resp.json()
