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

DEFAULT_MODEL = "gemini-3.8-flash"
"""Scores 100% / 5-of-5 exact on the fixture set, matching Bedrock's Claude Haiku. gemini-2.5-flash
reached only 92.6% / 0-of-5 on the same prompt, so the gap was the model generation, not the
prompt — no Gemini-specific prompt tuning was needed."""

DEFAULT_LOCATION = "global"
"""Gemini 3.x is served ONLY from the `global` endpoint. Requesting one from a regional endpoint
returns a 404 whose message suggests the model does not exist or is not permitted, which reads as
a naming or access problem rather than a location one."""
MAX_OUTPUT_TOKENS = 2048
"""Matches the Bedrock path, so the accuracy comparison is like-for-like."""

THINKING_LEVEL = "LOW"
"""Gemini 3.x controls reasoning with `thinking_level`; 2.x uses `thinking_budget`. The two are
mutually exclusive — each generation rejects the other's field with a 400.

Thinking **cannot be switched off** on gemini-3.8-flash. `MINIMAL` is rejected as unsupported and
`thinking_budget=0` is silently ignored, so `LOW` is the floor. Measured over the fixture set it
saves ~5% against leaving it unset, with accuracy unchanged at 100% / 5-of-5.

That matters for cost forecasting: thinking bills as output, and it is roughly 60% of this
model's output spend. No setting recovers it."""


def _thinking_config(types, model_id: str):
    """Minimise reasoning tokens, in whichever dialect this model generation speaks.

    Gemini 2.x charges thinking against max_output_tokens, so leaving it unconstrained truncated
    responses mid-grid with FinishReason.MAX_TOKENS — a configuration failure that scored 0% and
    read as a recognition failure. Gemini 3.x reasons outside that budget but still bills it as
    output.

    Either way the prompt already asks for a visible <scratchpad>, so hidden reasoning duplicates
    work we are paying for twice.
    """
    if model_id.startswith("gemini-2"):
        return types.ThinkingConfig(thinking_budget=0)
    return types.ThinkingConfig(thinking_level=THINKING_LEVEL)


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
                    max_output_tokens=MAX_OUTPUT_TOKENS,
                    thinking_config=_thinking_config(types, model_id),
                ),
            )
        except Exception as exc:
            raise ProviderError(
                f"Vertex invocation failed for {model_id}: {exc}"
            ) from exc

        if not response.text:
            raise ProviderError(f"Vertex returned an empty response for {model_id}")
        return response.text
