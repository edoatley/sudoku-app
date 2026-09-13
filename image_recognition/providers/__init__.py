"""Vision provider port and runtime selection.

Mirrors the backend's `CoachAiClient` port: one seam, two adapters, chosen at runtime by
configuration. Consumers depend on the protocol, never on a concrete adapter.

**A provider turns an image into model text — it does not parse.** Grid parsing, scoring and the
cross-check loop stay in `handler`, which keeps the adapters tiny and means `providers` never
imports `handler`. An earlier draft returned a parsed grid and needed two in-function imports to
dodge the resulting circularity; that was the seam being in the wrong place.

@spec IR-AI-001, IR-AI-003, IR-AI-005
"""

from __future__ import annotations

import os
from typing import Protocol, runtime_checkable

DEFAULT_PROVIDER = "bedrock"
"""AWS Lambda runs unprofiled, so an absent IMAGE_AI_PROVIDER must keep the incumbent path."""

# Bedrock wants a bare format name; Vertex wants a MIME type. Detection is shared, vocabulary is
# the adapter's own.
_MIME = {
    "png": "image/png",
    "jpeg": "image/jpeg",
    "gif": "image/gif",
    "webp": "image/webp",
}


class ProviderError(RuntimeError):
    """A provider's SDK failed.

    Each adapter wraps its own SDK exceptions in this, so the recognition loop catches one type
    and never imports a vendor SDK. @spec IR-AI-005
    """


@runtime_checkable
class VisionProvider(Protocol):
    """Turns an image into raw model text. One call, one model, no retry — the caller owns the
    loop."""

    name: str
    models: list[str]

    def recognise(self, image_bytes: bytes, model_id: str) -> str:
        """Return the model's raw text response. Raise ProviderError on SDK failure."""
        ...


def detect_image_format(image_bytes: bytes) -> str:
    """Sniff the container format from magic bytes. Returns a bare name ('png', 'jpeg', ...)."""
    if image_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if image_bytes.startswith(b"GIF8"):
        return "gif"
    if image_bytes[:4] == b"RIFF" and image_bytes[8:12] == b"WEBP":
        return "webp"
    return "jpeg"


def mime_type(image_bytes: bytes) -> str:
    """The same detection, in MIME vocabulary — what Vertex expects."""
    return _MIME[detect_image_format(image_bytes)]


def get_provider(name: str | None = None) -> VisionProvider:
    """Select the adapter from IMAGE_AI_PROVIDER, defaulting to bedrock.

    The SDK import happens inside each branch, so the Lambda image never loads google-genai and
    the Cloud Run image never constructs a boto3 client — the Python equivalent of the backend's
    @LookupIfProperty. @spec IR-AI-003
    """
    provider = (name or os.environ.get("IMAGE_AI_PROVIDER") or DEFAULT_PROVIDER).lower()

    if provider == "bedrock":
        from .bedrock import BedrockVisionProvider

        return BedrockVisionProvider()
    if provider == "vertex":
        from .vertex import VertexVisionProvider

        return VertexVisionProvider()

    # Fail loud. Silently falling back would make a typo look like a working deployment that
    # quietly bills the wrong cloud.
    raise ValueError(
        f"Unknown IMAGE_AI_PROVIDER {provider!r} — expected 'bedrock' or 'vertex'"
    )
