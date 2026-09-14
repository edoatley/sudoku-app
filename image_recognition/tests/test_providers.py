"""Provider port: selection, isolation and error wrapping.

@spec IR-AI-001, IR-AI-002, IR-AI-003, IR-AI-004, IR-AI-005, IR-AI-006
"""

from __future__ import annotations

import os
from unittest.mock import patch

import prompts
import providers
import pytest


class TestSelection:
    def test_defaults_to_bedrock_when_unset(self):
        # AWS Lambda runs unprofiled; an absent variable must keep the incumbent path.
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("IMAGE_AI_PROVIDER", None)
            assert providers.get_provider().name == "bedrock"

    @pytest.mark.parametrize("value", ["bedrock", "BEDROCK", "  bedrock  ".strip()])
    def test_bedrock_selected_case_insensitively(self, value):
        with patch.dict(os.environ, {"IMAGE_AI_PROVIDER": value}):
            assert providers.get_provider().name == "bedrock"

    def test_vertex_selected(self):
        with patch.dict(
            os.environ, {"IMAGE_AI_PROVIDER": "vertex", "GCP_PROJECT_ID": "p"}
        ):
            assert providers.get_provider().name == "vertex"

    def test_unknown_value_fails_loudly(self):
        # Silently falling back would make a typo look like a working deployment that quietly
        # bills the wrong cloud.
        with (
            patch.dict(os.environ, {"IMAGE_AI_PROVIDER": "gemini"}),
            pytest.raises(ValueError, match="Unknown IMAGE_AI_PROVIDER"),
        ):
            providers.get_provider()

    def test_vertex_without_a_project_fails_loudly(self):
        with patch.dict(os.environ, {"IMAGE_AI_PROVIDER": "vertex"}, clear=False):
            os.environ.pop("GCP_PROJECT_ID", None)
            with pytest.raises(ValueError, match="GCP_PROJECT_ID"):
                providers.get_provider()


class TestModelLists:
    def test_bedrock_keeps_its_env_var_name(self):
        # BEDROCK_MODELS must not be renamed: infra/aws derives both the IAM resource list and
        # this variable from one Terraform local. @spec IR-AI-006
        with patch.dict(os.environ, {"BEDROCK_MODELS": "model-a, model-b"}):
            assert providers.get_provider("bedrock").models == ["model-a", "model-b"]

    def test_vertex_has_its_own_list(self):
        with patch.dict(
            os.environ, {"VERTEX_MODELS": "gemini-x", "GCP_PROJECT_ID": "p"}
        ):
            assert providers.get_provider("vertex").models == ["gemini-x"]

    def test_vertex_defaults_to_the_model_that_passed_the_accuracy_gate(self):
        # gemini-3.8-flash scores 100% / 5-of-5, matching Bedrock. gemini-2.5-flash managed
        # 92.6% / 0-of-5 on the identical prompt. Downgrading this is a measurable regression,
        # so it must be a decision rather than a drift.
        with patch.dict(os.environ, {"GCP_PROJECT_ID": "p"}, clear=False):
            os.environ.pop("VERTEX_MODELS", None)
            assert providers.get_provider("vertex").models == ["gemini-3.8-flash"]

    def test_vertex_defaults_to_the_global_endpoint(self):
        # Gemini 3.x is served only from `global`; a regional endpoint 404s with a message that
        # blames the model name rather than the location.
        with patch.dict(os.environ, {"GCP_PROJECT_ID": "p"}, clear=False):
            os.environ.pop("GCP_REGION", None)
            assert providers.get_provider("vertex")._location == "global"


class TestSharedPrompts:
    def test_both_adapters_import_the_same_prompts(self):
        # A provider swap must not be able to change recognition by changing the prompt.
        # @spec IR-AI-004
        from providers import bedrock as b
        from providers import vertex as v

        assert b.SYSTEM_PROMPT is prompts.SYSTEM_PROMPT is v.SYSTEM_PROMPT
        assert b.USER_PROMPT is prompts.USER_PROMPT is v.USER_PROMPT

    def test_neither_adapter_redefines_a_prompt(self):
        import pathlib

        root = pathlib.Path(__file__).resolve().parent.parent / "providers"
        for f in ("bedrock.py", "vertex.py"):
            src = (root / f).read_text()
            assert "SYSTEM_PROMPT = (" not in src, f"{f} redefines the system prompt"
            assert "USER_PROMPT = (" not in src, f"{f} redefines the user prompt"


class TestImageFormat:
    def test_bedrock_gets_a_bare_format_name(self):
        assert providers.detect_image_format(b"\x89PNG\r\n\x1a\n") == "png"

    def test_vertex_gets_a_mime_type(self):
        # Same detection, different vocabulary — the reason detection is shared.
        assert providers.mime_type(b"\x89PNG\r\n\x1a\n") == "image/png"
        assert providers.mime_type(b"\xff\xd8\xff") == "image/jpeg"


class TestIsolation:
    def test_selecting_bedrock_does_not_import_the_vertex_sdk(self):
        # The Lambda image must never load google-genai. @spec IR-AI-003
        import sys

        for mod in [m for m in sys.modules if m.startswith("google.genai")]:
            del sys.modules[mod]
        with patch.dict(os.environ, {"IMAGE_AI_PROVIDER": "bedrock"}):
            providers.get_provider()
        assert not [m for m in sys.modules if m.startswith("google.genai")]


class TestVertexAdapter:
    """The Vertex call path, with google.genai mocked.

    Covers what unit tests can: that the shared prompts and a MIME type reach the SDK, and that
    every SDK failure becomes a ProviderError so the recognition loop stays provider-agnostic.
    Whether Gemini reads grids *well* is not a unit-test question — that is the accuracy harness.
    """

    def _provider(self, fake_client):
        from providers.vertex import VertexVisionProvider

        p = VertexVisionProvider.__new__(VertexVisionProvider)
        p.models = ["gemini-2.5-flash"]
        p._project = "sudoku-eo-2026"
        p._location = "us-central1"
        p._client = fake_client
        return p

    def _fake_client(self, text=None, exc=None):
        from unittest.mock import MagicMock

        client = MagicMock()
        if exc is not None:
            client.models.generate_content.side_effect = exc
        else:
            client.models.generate_content.return_value = MagicMock(text=text)
        return client

    def test_returns_the_models_raw_text(self):
        client = self._fake_client(text='{"originalGrid": []}')
        out = self._provider(client).recognise(b"\x89PNG\r\n\x1a\n", "gemini-2.5-flash")
        assert out == '{"originalGrid": []}'

    def test_forwards_the_model_id(self):
        client = self._fake_client(text="x")
        self._provider(client).recognise(b"\xff\xd8\xff", "gemini-2.5-pro")
        assert client.models.generate_content.call_args[1]["model"] == "gemini-2.5-pro"

    def test_sends_the_shared_system_prompt(self):
        client = self._fake_client(text="x")
        self._provider(client).recognise(b"\xff\xd8\xff", "gemini-2.5-flash")
        cfg = client.models.generate_content.call_args[1]["config"]
        assert cfg.system_instruction == prompts.SYSTEM_PROMPT

    def test_uses_deterministic_settings(self):
        # temperature=0 matches the Bedrock path; a drift here would skew the accuracy comparison.
        client = self._fake_client(text="x")
        self._provider(client).recognise(b"\xff\xd8\xff", "gemini-2.5-flash")
        cfg = client.models.generate_content.call_args[1]["config"]
        assert cfg.temperature == 0
        assert cfg.max_output_tokens == 2048

    def test_sdk_failure_surfaces_as_provider_error(self):
        # @spec IR-AI-005 — the loop catches one type and never imports a vendor SDK.
        client = self._fake_client(exc=RuntimeError("quota exceeded"))
        with pytest.raises(providers.ProviderError, match="Vertex invocation failed"):
            self._provider(client).recognise(b"\xff\xd8\xff", "gemini-2.5-flash")

    def test_empty_response_is_an_error_not_a_silent_empty_grid(self):
        client = self._fake_client(text="")
        with pytest.raises(providers.ProviderError, match="empty response"):
            self._provider(client).recognise(b"\xff\xd8\xff", "gemini-2.5-flash")
