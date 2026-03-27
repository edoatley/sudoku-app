"""
Unit tests for handler.py — pure Python, no AWS calls required.

Tests cover:
  - _parse_grid: valid JSON, markdown fences, embedded JSON, error cases
  - _downscale_image: resizes large images, leaves small images unchanged, handles corrupt bytes
  - handler(): request validation (missing body, missing field, oversized payload)
"""
from __future__ import annotations

import base64
import io
import json

import pytest
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
