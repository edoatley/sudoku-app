"""
Unit tests for handler.py — pure Python, no AWS calls required.

Tests cover:
  - _parse_grid: valid JSON, markdown fences, embedded JSON, error cases, pipe fallback
  - _recognize: model scoring and error handling (fake provider)
  - providers.bedrock: the Converse API call (boto3 client mocked)
  - handler(): request validation, success path, 422 and 500 error paths
"""

from __future__ import annotations

import base64
import io
import json
from unittest.mock import MagicMock, patch

import handler
import prompts
import providers
import pytest
from botocore.exceptions import ClientError
from PIL import Image

# ---------------------------------------------------------------------------
# _parse_grid
# ---------------------------------------------------------------------------


class TestParseGrid:
    def _make_grid(self, fill: int = 0) -> list[list[int]]:
        return [[fill] * 9 for _ in range(9)]

    def _wrap(self, grid: list[list[int]]) -> str:
        return json.dumps({"originalGrid": grid})

    def test_valid_json_all_zeros(self):
        grid = self._make_grid(0)
        assert handler._parse_grid(self._wrap(grid)) == grid

    def test_valid_json_with_digits(self):
        grid = self._make_grid(0)
        grid[0][0] = 5
        grid[4][4] = 9
        result = handler._parse_grid(self._wrap(grid))
        assert result[0][0] == 5
        assert result[4][4] == 9

    def test_strips_markdown_fences(self):
        grid = self._make_grid(1)
        raw = "```json\n" + self._wrap(grid) + "\n```"
        assert handler._parse_grid(raw) == grid

    def test_strips_backtick_fence_no_lang(self):
        grid = self._make_grid(2)
        raw = "```\n" + self._wrap(grid) + "\n```"
        assert handler._parse_grid(raw) == grid

    def test_extracts_json_from_prose(self):
        grid = self._make_grid(3)
        raw = "Here is the result: " + self._wrap(grid) + " Hope that helps!"
        assert handler._parse_grid(raw) == grid

    def test_raises_if_no_json_object(self):
        with pytest.raises(ValueError, match="No JSON object found"):
            handler._parse_grid("This is just plain text with no braces")

    def test_raises_if_original_grid_missing(self):
        with pytest.raises(ValueError, match="'originalGrid'"):
            handler._parse_grid('{"something": "else"}')

    def test_raises_if_grid_has_wrong_number_of_rows(self):
        bad = {"originalGrid": [[0] * 9 for _ in range(8)]}
        with pytest.raises(ValueError, match="9-element list"):
            handler._parse_grid(json.dumps(bad))

    def test_raises_if_row_has_wrong_length(self):
        grid = [[0] * 9 for _ in range(9)]
        grid[3] = [0] * 8  # one short
        with pytest.raises(ValueError, match="Row 3"):
            handler._parse_grid(json.dumps({"originalGrid": grid}))

    def test_raises_if_cell_value_out_of_range(self):
        grid = [[0] * 9 for _ in range(9)]
        grid[0][0] = 10  # invalid
        with pytest.raises(ValueError, match=r"Cell \[0\]\[0\]"):
            handler._parse_grid(json.dumps({"originalGrid": grid}))

    def test_coerces_string_digit_to_int(self):
        grid = [[0] * 9 for _ in range(9)]
        grid[2][2] = "5"
        result = handler._parse_grid(json.dumps({"originalGrid": grid}))
        assert result[2][2] == 5

    def test_raises_if_cell_value_is_non_numeric_string(self):
        grid = [[0] * 9 for _ in range(9)]
        grid[2][2] = "X"
        with pytest.raises(ValueError, match=r"Cell \[2\]\[2\]"):
            handler._parse_grid(json.dumps({"originalGrid": grid}))

    def test_raises_on_invalid_json(self):
        with pytest.raises(ValueError):
            handler._parse_grid("{not valid json}")

    def test_scratchpad_pipe_fallback(self):
        """When there is no JSON, the parser recovers the grid from pipe-delimited rows."""
        rows = [
            "| 5 | 3 | . | . | 7 | . | . | . | . |",
            "| 6 | . | . | 1 | 9 | 5 | . | . | . |",
            "| . | 9 | 8 | . | . | . | . | 6 | . |",
            "| 8 | . | . | . | 6 | . | . | . | 3 |",
            "| 4 | . | . | 8 | . | 3 | . | . | 1 |",
            "| 7 | . | . | . | 2 | . | . | . | 6 |",
            "| . | 6 | . | . | . | . | 2 | 8 | . |",
            "| . | . | . | 4 | 1 | 9 | . | . | 5 |",
            "| . | . | . | . | 8 | . | . | 7 | 9 |",
        ]
        text = "Here is my scratchpad:\n" + "\n".join(rows)
        expected = [
            [5, 3, 0, 0, 7, 0, 0, 0, 0],
            [6, 0, 0, 1, 9, 5, 0, 0, 0],
            [0, 9, 8, 0, 0, 0, 0, 6, 0],
            [8, 0, 0, 0, 6, 0, 0, 0, 3],
            [4, 0, 0, 8, 0, 3, 0, 0, 1],
            [7, 0, 0, 0, 2, 0, 0, 0, 6],
            [0, 6, 0, 0, 0, 0, 2, 8, 0],
            [0, 0, 0, 4, 1, 9, 0, 0, 5],
            [0, 0, 0, 0, 8, 0, 0, 7, 9],
        ]
        assert handler._parse_grid(text) == expected


# ---------------------------------------------------------------------------
# _detect_image_format
# ---------------------------------------------------------------------------


def _make_jpeg(width: int, height: int) -> bytes:
    img = Image.new("RGB", (width, height), color=(128, 64, 32))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def _make_png(width: int = 10, height: int = 10) -> bytes:
    img = Image.new("RGB", (width, height), color=(0, 0, 0))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


# ---------------------------------------------------------------------------
# _SYSTEM_PROMPT
# ---------------------------------------------------------------------------


class TestSystemPrompt:
    # @spec IR-PROC-013
    def test_system_prompt_contains_colour_hint(self):
        """System prompt must instruct the model to ignore cell background colour."""
        prompt_lower = prompts.SYSTEM_PROMPT.lower()
        assert "colour" in prompt_lower or "color" in prompt_lower, (
            "_SYSTEM_PROMPT must contain a colour/color hint for shaded-cell puzzles"
        )
        assert "background" in prompt_lower, (
            "_SYSTEM_PROMPT must mention 'background' so the model ignores cell shading"
        )


class TestDetectImageFormat:
    def test_jpeg_bytes_detected(self):
        assert providers.detect_image_format(_make_jpeg(10, 10)) == "jpeg"

    def test_png_bytes_detected(self):
        assert providers.detect_image_format(_make_png()) == "png"

    def test_unknown_bytes_default_to_jpeg(self):
        assert providers.detect_image_format(b"\x00\x01\x02\x03") == "jpeg"

    def test_gif_bytes_detected(self):
        assert providers.detect_image_format(b"GIF89a\x00\x00") == "gif"

    def test_webp_bytes_detected(self):
        assert providers.detect_image_format(b"RIFF\x00\x00\x00\x00WEBP") == "webp"


# ---------------------------------------------------------------------------
# _has_row_col_box_duplicate
# ---------------------------------------------------------------------------


class TestHasRowColBoxDuplicate:
    def _clean_grid(self) -> list[list[int]]:
        return [[0] * 9 for _ in range(9)]

    def test_all_zeros_no_duplicate(self):
        assert not handler._has_row_col_box_duplicate(self._clean_grid())

    def test_distinct_values_no_duplicate(self):
        grid = self._clean_grid()
        grid[0][0] = 1
        grid[0][8] = 2
        grid[8][0] = 3
        assert not handler._has_row_col_box_duplicate(grid)

    def test_detects_row_duplicate(self):
        grid = self._clean_grid()
        grid[0][0] = 5
        grid[0][5] = 5
        assert handler._has_row_col_box_duplicate(grid)

    def test_detects_column_duplicate(self):
        grid = self._clean_grid()
        grid[0][3] = 7
        grid[6][3] = 7
        assert handler._has_row_col_box_duplicate(grid)

    def test_detects_box_duplicate(self):
        grid = self._clean_grid()
        grid[0][0] = 4
        grid[2][2] = 4  # same top-left 3x3 box
        assert handler._has_row_col_box_duplicate(grid)

    def test_zeros_not_counted_as_duplicates(self):
        grid = self._clean_grid()
        # entire grid is zeros — must not trigger
        assert not handler._has_row_col_box_duplicate(grid)


# ---------------------------------------------------------------------------
# handler() — request validation (no AWS calls)
# ---------------------------------------------------------------------------


# ---------------------------------------------------------------------------
# handler() — warmup probe (IR-API-002)
# ---------------------------------------------------------------------------


class TestHandlerWarmup:
    def test_warmup_returns_200(self):
        response = handler.handler({"rawPath": "/api/v1/puzzles/import/warmup"}, None)
        assert response["statusCode"] == 200

    def test_warmup_does_not_invoke_any_provider(self):
        """Warmup must not call a model. Patching the seam rather than boto3 makes this
        assertion provider-agnostic: it now holds for Vertex too."""
        with patch("handler.get_provider") as mock_get_provider:
            handler.handler({"rawPath": "/api/v1/puzzles/import/warmup"}, None)
            mock_get_provider.assert_not_called()

    def test_non_warmup_path_not_intercepted(self):
        """A path that doesn't end with /warmup falls through to normal validation."""
        response = handler.handler({"rawPath": "/api/v1/puzzles/import"}, None)
        assert response["statusCode"] == 400  # no body → normal validation kicks in


# ---------------------------------------------------------------------------
# handler() — request validation (no AWS calls)
# ---------------------------------------------------------------------------


class TestHandlerRequestValidation:
    def test_missing_body_returns_400(self):
        response = handler.handler({}, None)
        assert response["statusCode"] == 400
        assert "image" in json.loads(response["body"])["error"]

    def test_empty_body_returns_400(self):
        response = handler.handler({"body": ""}, None)
        assert response["statusCode"] == 400

    def test_body_not_json_returns_400(self):
        response = handler.handler({"body": "not-json"}, None)
        assert response["statusCode"] == 400

    def test_body_missing_image_field_returns_400(self):
        response = handler.handler({"body": json.dumps({"other": "field"})}, None)
        assert response["statusCode"] == 400

    def test_image_field_empty_string_returns_400(self):
        response = handler.handler({"body": json.dumps({"image": ""})}, None)
        assert response["statusCode"] == 400

    def test_oversized_image_returns_400(self):
        # 9 MB of base64-encoded zeros
        big = base64.b64encode(b"\x00" * (9 * 1024 * 1024)).decode()
        response = handler.handler({"body": json.dumps({"image": big})}, None)
        assert response["statusCode"] == 400
        assert "8 MB" in json.loads(response["body"])["error"]

    def test_valid_image_returns_200(self):
        """A valid image with a mocked Bedrock call returns 200 with originalGrid and modelName."""
        grid = [[0] * 9 for _ in range(9)]
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with (
            patch("handler.get_provider"),
            patch(
                "handler._recognize",
                return_value=(grid, True, "us.amazon.nova-pro-v1:0"),
            ),
        ):
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 200
        body = json.loads(response["body"])
        assert body["originalGrid"] == grid
        assert body["validPuzzle"] is True
        assert body["modelName"] == "us.amazon.nova-pro-v1:0"

    def test_invalid_grid_returns_422(self):
        """When _recognize returns valid=False, handler returns 422."""
        grid = [[0] * 9 for _ in range(9)]
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with (
            patch("handler.get_provider"),
            patch(
                "handler._recognize",
                return_value=(grid, False, "mistral.magistral-small-2509"),
            ),
        ):
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 422
        assert "valid Sudoku grid" in json.loads(response["body"])["error"]

    def test_recognize_raises_value_error_returns_422(self):
        """When _recognize raises ValueError, handler returns 422."""
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with (
            patch("handler.get_provider"),
            patch(
                "handler._recognize",
                side_effect=ValueError("no grid found"),
            ),
        ):
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 422
        assert "no grid found" in json.loads(response["body"])["error"]

    def test_recognize_raises_unexpected_exception_returns_500(self):
        """When _recognize raises an unexpected error, handler returns 500."""
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with (
            patch("handler.get_provider"),
            patch("handler._recognize", side_effect=RuntimeError("boom")),
        ):
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 500


# ---------------------------------------------------------------------------
# _recognize (fake provider) and the Bedrock adapter (boto3 client mocked)
# ---------------------------------------------------------------------------


def _make_mock_client(response_text: str) -> MagicMock:
    """Return a mock boto3 bedrock-runtime client that returns the given text."""
    client = MagicMock()
    client.converse.return_value = {
        "output": {"message": {"content": [{"text": response_text}]}}
    }
    return client


def _grid_json(grid: list[list[int]]) -> str:
    return json.dumps({"originalGrid": grid})


# A clean grid with 25 clues and no duplicates
_CLEAN_GRID = [
    [5, 3, 0, 0, 7, 0, 0, 0, 0],
    [6, 0, 0, 1, 9, 5, 0, 0, 0],
    [0, 9, 8, 0, 0, 0, 0, 6, 0],
    [8, 0, 0, 0, 6, 0, 0, 0, 3],
    [4, 0, 0, 8, 0, 3, 0, 0, 1],
    [7, 0, 0, 0, 2, 0, 0, 0, 6],
    [0, 6, 0, 0, 0, 0, 2, 8, 0],
    [0, 0, 0, 4, 1, 9, 0, 0, 5],
    [0, 0, 0, 0, 8, 0, 0, 7, 9],
]

# A grid with a row-duplicate (5 appears twice in row 0)
_DUPE_GRID = [
    [5, 5, 0, 0, 7, 0, 0, 0, 0],
    [6, 0, 0, 1, 9, 4, 0, 0, 0],
    [0, 9, 8, 0, 0, 0, 0, 6, 0],
    [8, 0, 0, 0, 6, 0, 0, 0, 3],
    [4, 0, 0, 8, 0, 3, 0, 0, 1],
    [7, 0, 0, 0, 2, 0, 0, 0, 6],
    [0, 6, 0, 0, 0, 0, 2, 8, 0],
    [0, 0, 0, 4, 1, 9, 0, 0, 5],
    [0, 0, 0, 0, 8, 0, 0, 7, 9],
]

# A grid with only 3 clues (below _MIN_PLAUSIBLE_CLUES)
_SPARSE_GRID = [[0] * 9 for _ in range(9)]
_SPARSE_GRID[0][0] = 1
_SPARSE_GRID[1][1] = 2
_SPARSE_GRID[2][2] = 3

# An alternative clean grid (different from _CLEAN_GRID) with 25 clues and no duplicates
_CLEAN_GRID_ALT = [
    [0, 0, 3, 0, 2, 0, 6, 0, 0],
    [9, 0, 0, 3, 0, 5, 0, 0, 1],
    [0, 0, 1, 8, 0, 6, 4, 0, 0],
    [0, 0, 8, 1, 0, 2, 9, 0, 0],
    [7, 0, 0, 0, 0, 0, 0, 0, 8],
    [0, 0, 6, 7, 0, 8, 2, 0, 0],
    [0, 0, 2, 6, 0, 9, 5, 0, 0],
    [8, 0, 0, 2, 0, 3, 0, 0, 9],
    [0, 0, 5, 0, 1, 0, 3, 0, 0],
]


class _FakeProvider:
    """Stands in for any VisionProvider. The loop under test must not care which cloud it is."""

    name = "fake"

    def __init__(self, responses, models=("model-a",)):
        self.models = list(models)
        self._responses = responses if isinstance(responses, list) else [responses]
        self.calls = []

    def recognise(self, image_bytes, model_id):
        self.calls.append(model_id)
        value = self._responses[min(len(self.calls) - 1, len(self._responses) - 1)]
        if isinstance(value, Exception):
            raise value
        return value


class TestRecognize:
    """What TestRecognizeWithBedrock asserted, now provider-agnostic. The scoring, cross-check
    and duplicate-detection logic is unchanged — only its collaborator is."""

    def test_model_success_returns_valid_grid(self):
        provider = _FakeProvider(_grid_json(_CLEAN_GRID))
        grid, valid, model_name = handler._recognize(provider, b"fake-image")
        assert grid == _CLEAN_GRID
        assert valid is True
        assert model_name == provider.models[0]
        assert len(provider.calls) == 1

    def test_model_too_few_clues_raises_value_error(self):
        provider = _FakeProvider(_grid_json(_SPARSE_GRID))
        with pytest.raises(ValueError, match="All models failed"):
            handler._recognize(provider, b"fake-image")

    def test_model_returns_duplicates_returns_invalid(self):
        provider = _FakeProvider(_grid_json(_DUPE_GRID))
        _grid, valid, _model = handler._recognize(provider, b"fake-image")
        assert valid is False

    def test_provider_error_raises_value_error(self):
        """A provider SDK failure is absorbed by the same loop that handled ClientError.
        @spec IR-AI-005"""
        provider = _FakeProvider(providers.ProviderError("throttled"))
        with pytest.raises(ValueError, match="All models failed"):
            handler._recognize(provider, b"fake-image")


class TestBedrockAdapter:
    """What TestInvokeModel asserted, retargeted at providers/bedrock.py. The adapter returns raw
    model text now — parsing moved to the caller — so grid assertions go through _parse_grid."""

    def _provider(self, client):
        from providers.bedrock import BedrockVisionProvider

        p = BedrockVisionProvider.__new__(BedrockVisionProvider)
        p.models = ["amazon.nova-pro-v1:0"]
        p._client = client
        return p

    def test_returns_text_that_parses_to_the_expected_grid(self):
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        text = self._provider(client).recognise(b"fake-image", "amazon.nova-pro-v1:0")
        assert handler._parse_grid(text) == _CLEAN_GRID

    def test_passes_correct_model_id(self):
        model_id = "amazon.nova-lite-v1:0"
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        self._provider(client).recognise(b"fake-image", model_id)
        assert client.converse.call_args[1]["modelId"] == model_id

    def test_malformed_text_raises_on_parse(self):
        client = _make_mock_client("This is not JSON at all")
        text = self._provider(client).recognise(b"fake-image", "amazon.nova-pro-v1:0")
        with pytest.raises(ValueError):
            handler._parse_grid(text)

    def test_jpeg_image_uses_jpeg_format(self):
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        self._provider(client).recognise(_make_jpeg(10, 10), "amazon.nova-pro-v1:0")
        content = client.converse.call_args[1]["messages"][0]["content"][0]["image"]
        assert content["format"] == "jpeg"

    def test_png_image_uses_png_format(self):
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        self._provider(client).recognise(_make_png(), "amazon.nova-pro-v1:0")
        content = client.converse.call_args[1]["messages"][0]["content"][0]["image"]
        assert content["format"] == "png"

    def test_sdk_failure_surfaces_as_provider_error(self):
        """@spec IR-AI-005"""
        client = MagicMock()
        client.converse.side_effect = ClientError(
            {"Error": {"Code": "ThrottlingException"}}, "Converse"
        )
        with pytest.raises(providers.ProviderError):
            self._provider(client).recognise(b"fake-image", "amazon.nova-pro-v1:0")
