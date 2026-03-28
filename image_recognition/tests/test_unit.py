"""
Unit tests for handler.py — pure Python, no AWS calls required.

Tests cover:
  - _parse_grid: valid JSON, markdown fences, embedded JSON, error cases, pipe fallback
  - _downscale_image: resizes large images, leaves small images unchanged, handles corrupt bytes, RGBA
  - _recognize_with_bedrock: model cascade, scoring, error handling (boto3 client mocked)
  - _invoke_model: Bedrock Converse API call (boto3 client mocked)
  - handler(): request validation, success path, 422 and 500 error paths
"""
from __future__ import annotations

import base64
import io
import json
from unittest.mock import MagicMock, patch

import pytest
from botocore.exceptions import ClientError
from PIL import Image

import handler


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

    def test_raises_if_cell_value_is_string(self):
        grid = [[0] * 9 for _ in range(9)]
        grid[2][2] = "5"
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
# _downscale_image
# ---------------------------------------------------------------------------


def _make_jpeg(width: int, height: int) -> bytes:
    img = Image.new("RGB", (width, height), color=(128, 64, 32))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


class TestDownscaleImage:

    def test_large_image_is_resized(self):
        original = _make_jpeg(1600, 1200)
        result = handler._downscale_image(original)
        img = Image.open(io.BytesIO(result))
        assert max(img.size) <= handler._MAX_IMAGE_EDGE

    def test_small_image_is_not_enlarged(self):
        original = _make_jpeg(400, 300)
        result = handler._downscale_image(original)
        img = Image.open(io.BytesIO(result))
        # dimensions should be unchanged (or very close after JPEG round-trip)
        assert img.width <= 400
        assert img.height <= 300

    def test_square_image_at_exact_limit_unchanged(self):
        size = handler._MAX_IMAGE_EDGE
        original = _make_jpeg(size, size)
        result = handler._downscale_image(original)
        img = Image.open(io.BytesIO(result))
        assert max(img.size) == size

    def test_output_is_jpeg(self):
        original = _make_jpeg(1000, 800)
        result = handler._downscale_image(original)
        img = Image.open(io.BytesIO(result))
        assert img.format == "JPEG"

    def test_corrupt_bytes_returns_original(self):
        corrupt = b"\xff\xd8\xff\x00garbage bytes that are not a valid image"
        result = handler._downscale_image(corrupt)
        assert result == corrupt

    def test_output_is_greyscale_normalised_to_rgb(self):
        """Orange pixels should become grey (R≈G≈B) after downscale."""
        img = Image.new("RGB", (100, 100), color=(255, 100, 0))  # vivid orange
        buf = io.BytesIO()
        img.save(buf, format="JPEG")
        result = handler._downscale_image(buf.getvalue())
        out = Image.open(io.BytesIO(result))
        assert out.mode == "RGB"
        px = out.getpixel((50, 50))
        # After full desaturation (Color.enhance(0.0)) all channels are equal;
        # allow ±5 for JPEG rounding and contrast/sharpness post-processing.
        assert abs(px[0] - px[1]) <= 5 and abs(px[1] - px[2]) <= 5

    def test_rgba_image_composited_onto_white(self):
        """RGBA images (with transparency) should be composited onto white and output as JPEG."""
        img = Image.new("RGBA", (100, 100), color=(0, 0, 255, 128))  # semi-transparent blue
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        result = handler._downscale_image(buf.getvalue())
        out = Image.open(io.BytesIO(result))
        assert out.format == "JPEG"
        assert out.mode == "RGB"


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
        """A valid image with a mocked Bedrock call returns 200 with originalGrid."""
        grid = [[0] * 9 for _ in range(9)]
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with patch("handler.boto3") as mock_boto3, \
             patch("handler._recognize_with_bedrock", return_value=(grid, True)):
            mock_boto3.client.return_value = MagicMock()
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 200
        body = json.loads(response["body"])
        assert body["originalGrid"] == grid
        assert body["validPuzzle"] is True

    def test_recognize_raises_value_error_returns_422(self):
        """When _recognize_with_bedrock raises ValueError, handler returns 422."""
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with patch("handler.boto3") as mock_boto3, \
             patch("handler._recognize_with_bedrock", side_effect=ValueError("no grid found")):
            mock_boto3.client.return_value = MagicMock()
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 422
        assert "no grid found" in json.loads(response["body"])["error"]

    def test_recognize_raises_unexpected_exception_returns_500(self):
        """When _recognize_with_bedrock raises an unexpected error, handler returns 500."""
        image_b64 = base64.b64encode(_make_jpeg(100, 100)).decode()
        with patch("handler.boto3") as mock_boto3, \
             patch("handler._recognize_with_bedrock", side_effect=RuntimeError("boom")):
            mock_boto3.client.return_value = MagicMock()
            response = handler.handler({"body": json.dumps({"image": image_b64})}, None)
        assert response["statusCode"] == 500


# ---------------------------------------------------------------------------
# _recognize_with_bedrock (boto3 client mocked)
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


class TestRecognizeWithBedrock:

    def test_first_model_success_returns_clean_grid(self):
        """A valid response from the first model returns (grid, True) immediately."""
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        grid, valid = handler._recognize_with_bedrock(client, b"fake-image")
        assert grid == _CLEAN_GRID
        assert valid is True
        # Early-exit: converse called exactly once
        assert client.converse.call_count == 1

    def test_first_model_too_few_clues_falls_through_to_second(self):
        """When the first model returns too few clues, the second model is tried."""
        client = MagicMock()
        client.converse.side_effect = [
            {"output": {"message": {"content": [{"text": _grid_json(_SPARSE_GRID)}]}}},
            {"output": {"message": {"content": [{"text": _grid_json(_CLEAN_GRID)}]}}},
        ]
        grid, valid = handler._recognize_with_bedrock(client, b"fake-image")
        assert grid == _CLEAN_GRID
        assert valid is True
        assert client.converse.call_count == 2

    def test_first_model_has_duplicates_tries_second(self):
        """When the first model returns a grid with duplicates, the second is tried."""
        client = MagicMock()
        client.converse.side_effect = [
            {"output": {"message": {"content": [{"text": _grid_json(_DUPE_GRID)}]}}},
            {"output": {"message": {"content": [{"text": _grid_json(_CLEAN_GRID)}]}}},
        ]
        grid, valid = handler._recognize_with_bedrock(client, b"fake-image")
        assert grid == _CLEAN_GRID
        assert valid is True

    def test_all_models_fail_raises_value_error(self):
        """When all models raise ClientError, ValueError is raised."""
        client = MagicMock()
        error_response = {"Error": {"Code": "ThrottlingException", "Message": "Rate exceeded"}}
        client.converse.side_effect = ClientError(error_response, "Converse")
        with pytest.raises(ValueError, match="All models failed"):
            handler._recognize_with_bedrock(client, b"fake-image")

    def test_all_models_return_duplicates_returns_invalid(self):
        """When every model returns a grid with duplicates, valid is False."""
        client = _make_mock_client(_grid_json(_DUPE_GRID))
        grid, valid = handler._recognize_with_bedrock(client, b"fake-image")
        assert grid == _DUPE_GRID
        assert valid is False


# ---------------------------------------------------------------------------
# _invoke_model (boto3 client mocked)
# ---------------------------------------------------------------------------


class TestInvokeModel:

    def test_returns_parsed_grid(self):
        """A valid Bedrock response is parsed into the expected grid."""
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        result = handler._invoke_model(client, "amazon.nova-pro-v1:0", b"fake-image")
        assert result == _CLEAN_GRID

    def test_passes_correct_model_id(self):
        """The modelId passed to _invoke_model is forwarded to client.converse."""
        model_id = "amazon.nova-lite-v1:0"
        client = _make_mock_client(_grid_json(_CLEAN_GRID))
        handler._invoke_model(client, model_id, b"fake-image")
        call_kwargs = client.converse.call_args[1]
        assert call_kwargs["modelId"] == model_id

    def test_raises_on_invalid_response_text(self):
        """When the model returns malformed text, ValueError is raised."""
        client = _make_mock_client("This is not JSON at all")
        with pytest.raises(ValueError):
            handler._invoke_model(client, "amazon.nova-pro-v1:0", b"fake-image")
