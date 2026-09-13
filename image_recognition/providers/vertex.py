"""Vertex AI (Gemini) vision adapter — the GCP-native path.

Authenticated by Application Default Credentials, i.e. the Cloud Run runtime service account
(`sudoku-image-recognition-run`, which holds roles/aiplatform.user). **No AWS credential is
involved**, which is the whole point: this is what lets the cross-cloud Bedrock key and Secret
Manager be removed from the GCP project entirely.

Model is full `gemini-2.5-flash`, not the coach's `flash-lite`. Grid OCR is materially harder than
text generation, and the accuracy gate is the arbiter.

@spec IR-AI-002, IR-AI-005, IR-AI-006
"""

from __future__ import annotations

import os

from prompts import SYSTEM_PROMPT, USER_PROMPT

from . import ProviderError, mime_type

DEFAULT_MODEL = "gemini-2.5-flash"
DEFAULT_LOCATION = "us-central1"


class VertexVisionProvider:
    name = "vertex"

    def __init__(self) -> None:
        raw = os.environ.get("VERTEX_MODELS", DEFAULT_MODEL)
        self.models = [m.strip() for m in raw.split(",") if m.strip()]
        self._project = os.environ.get("GCP_PROJECT_ID")
        if not self._project:
            raise ValueError("IMAGE_AI_PROVIDER=vertex requires GCP_PROJECT_ID")
        self._location = os.environ.get("GCP_REGION", DEFAULT_LOCATION)
        self._client = None  # built lazily so construction stays cheap and import-safe

    def _get_client(self):
        if self._client is None:
            from google import genai

            self._client = genai.Client(
                vertexai=True, project=self._project, location=self._location
            )
        return self._client

    def recognise(self, image_bytes: bytes, model_id: str) -> str:
        from google.genai import types

        try:
            response = self._get_client().models.generate_content(
                model=model_id,
                contents=[
                    types.Part.from_bytes(
                        data=image_bytes, mime_type=mime_type(image_bytes)
                    ),
                    types.Part.from_text(text=USER_PROMPT),
                ],
                config=types.GenerateContentConfig(
                    system_instruction=SYSTEM_PROMPT,
                    temperature=0,
                    max_output_tokens=2048,
                ),
            )
        except Exception as exc:
            raise ProviderError(
                f"Vertex invocation failed for {model_id}: {exc}"
            ) from exc

        if not response.text:
            raise ProviderError(f"Vertex returned an empty response for {model_id}")
        return response.text
