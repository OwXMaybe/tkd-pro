"""Thin wrapper around the OpenAI API. The API key lives only on this server, never in the app."""

from dataclasses import dataclass

from fastapi import HTTPException, status

from ..config import Settings


@dataclass
class Completion:
    text: str
    model: str


class AiClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._client = None

    def _get(self):
        if not self.settings.ai_configured:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "AI provider is not configured (OPENAI_API_KEY)")
        if self._client is None:
            from openai import AsyncOpenAI

            self._client = AsyncOpenAI(api_key=self.settings.openai_api_key)
        return self._client

    async def complete(self, system: str, messages: list[dict[str, str]], max_tokens: int = 1200) -> Completion:
        client = self._get()
        try:
            response = await client.chat.completions.create(
                model=self.settings.openai_model,
                messages=[{"role": "system", "content": system}, *messages],
                temperature=0.3,
                max_tokens=max_tokens,
            )
        except Exception as exc:  # noqa: BLE001 - surface provider failures as 502 without leaking details
            raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"AI provider error: {type(exc).__name__}") from exc
        text = (response.choices[0].message.content or "").strip()
        if not text:
            raise HTTPException(status.HTTP_502_BAD_GATEWAY, "AI provider returned an empty answer")
        return Completion(text=text, model=response.model or self.settings.openai_model)
