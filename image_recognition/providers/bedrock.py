"""Amazon Bedrock vision adapter — the incumbent path, and the one AWS keeps.

Moved from handler._invoke_model unchanged apart from returning raw text and wrapping ClientError.
@spec IR-AI-002, IR-AI-005, IR-AI-006
"""

from __future__ import annotations

import os

import boto3
from botocore.exceptions import ClientError
from prompts import SYSTEM_PROMPT, USER_PROMPT

from . import ProviderError, detect_image_format

DEFAULT_MODEL = "eu.anthropic.claude-haiku-4-5-20251001-v1:0"
DEFAULT_REGION = "eu-west-2"
"""The eu. inference profile keeps inference in-region for data residency."""


class BedrockVisionProvider:
    name = "bedrock"

    def __init__(self) -> None:
        # BEDROCK_MODELS keeps its name so infra/aws needs no change — its Terraform derives both
        # the IAM resource list and this env var from one local.
        raw = os.environ.get("BEDROCK_MODELS", DEFAULT_MODEL)
        self.models = [m.strip() for m in raw.split(",") if m.strip()]
        self._client = boto3.client(
            "bedrock-runtime",
            region_name=os.environ.get("AWS_REGION_NAME", DEFAULT_REGION),
        )

    def recognise(self, image_bytes: bytes, model_id: str) -> str:
        """Invoke via the Converse API.

        Converse rather than InvokeModel because it supports a system prompt across all model
        families, which is what makes the JSON output reliable. @spec IR-PROC-010, IR-PROC-011
        """
        try:
            response = self._client.converse(
                modelId=model_id,
                system=[{"text": SYSTEM_PROMPT}],
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "image": {
                                    "format": detect_image_format(image_bytes),
                                    "source": {"bytes": image_bytes},
                                }
                            },
                            {"text": USER_PROMPT},
                        ],
                    }
                ],
                inferenceConfig={"maxTokens": 2048, "temperature": 0},
            )
        except ClientError as exc:
            raise ProviderError(
                f"Bedrock invocation failed for {model_id}: {exc}"
            ) from exc

        return response["output"]["message"]["content"][0]["text"]
