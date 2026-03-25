"""
AWS Lambda Handler — Sudoku image recognition via Amazon Bedrock.

Accepts a base64-encoded image via API Gateway and returns a 9x9 Sudoku grid.
Uses the Bedrock Converse API so the model receives a proper system prompt,
which is essential for structured JSON output.

Primary model:  Amazon Nova Pro  (on-demand, eu-west-2)
Fallback model: Amazon Nova Lite (on-demand, eu-west-2)
"""
from __future__ import annotations

import base64
import io
import json
import logging

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# ---------------------------------------------------------------------------
# Model cascade — tried in order, first valid result wins.
# Both are available on-demand in eu-west-2 with no extra form submission.
# ---------------------------------------------------------------------------
_MODELS = [
    "amazon.nova-pro-v1:0",   # best accuracy; slightly more expensive
    "amazon.nova-lite-v1:0",  # cheaper fallback
]

# Downscale to at most this many pixels on the longest edge before sending.
# Reduces image-token cost and latency with no accuracy loss for grid reading.
_MAX_IMAGE_EDGE = 800

# A valid Sudoku has at least 17 clues.  We use a lower threshold so that
# very sparse / near-empty grids trigger a retry with the next model.
_MIN_PLAUSIBLE_CLUES = 10

# ---------------------------------------------------------------------------
# Prompts
# ---------------------------------------------------------------------------
_SYSTEM_PROMPT = (
    "You are an automated data extraction API. "
    "Your only purpose is to read images of Sudoku grids and output strict, "
    "perfectly formatted JSON. "
    "You do not output markdown, greetings, or conversational text of any kind."
)

_USER_PROMPT = (
    "Analyze the provided image of a Sudoku puzzle.\n\n"
    "Instructions:\n"
    "1. Focus only on the 9x9 grid. Ignore all UI chrome, shadows, watermarks, "
    "and background objects outside the grid.\n"
    "2. Read cells left to right, top to bottom.\n"
    "3. A cell contains a digit ONLY if a printed numeral (1-9) is visibly drawn "
    "inside it. Cell background colour (orange, yellow, grey, white) is purely "
    "decorative and must NOT be used to infer a digit.\n"
    "4. The orange-highlighted cell is the app's currently selected cell and is "
    "almost always empty — output 0 for it unless a numeral is clearly printed "
    "inside it.\n"
    "5. Output 0 for every cell that has no printed numeral.\n\n"
    "Output the result as a JSON object with a single key 'originalGrid' "
    "whose value is a 2D array: a list of 9 lists, each containing 9 integers.\n\n"
    "Crucial: output ONLY the raw JSON object. "
    "Do not wrap it in ```json blocks or add any other text."
)


# ---------------------------------------------------------------------------
# Lambda entry point
# ---------------------------------------------------------------------------

def handler(event: dict, context: object) -> dict:
    """
    Lambda entry point.

    Expected event (API Gateway HTTP API proxy format):
      {
        "body": "{\"image\": \"<base64-encoded image bytes>\"}"
      }

    Successful response:
      {
        "statusCode": 200,
        "body": "{\"originalGrid\": [[0,0,...], ...]}"
      }

    where ``originalGrid`` is a 9x9 list of ints (0 = empty cell).
    """
    try:
        raw_body = event.get("body") or ""
        try:
            payload = json.loads(raw_body)
            image_b64 = payload.get("image", "") if isinstance(payload, dict) else ""
        except (json.JSONDecodeError, AttributeError):
            return _error(400, "Request body must be JSON with an 'image' field.")

        if not image_b64:
            return _error(400, "Request body must be JSON with an 'image' field.")

        image_bytes = base64.b64decode(image_b64)

        if len(image_bytes) > 8 * 1024 * 1024:
            return _error(400, "Image too large — maximum size is 8 MB.")

        # Downscale to reduce image-token cost; re-detect media type after
        image_bytes = _downscale_image(image_bytes)
        grid = _recognize_with_bedrock(image_bytes)

        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"originalGrid": grid}),
        }

    except ValueError as exc:
        logger.warning("Grid detection failed: %s", exc)
        return _error(422, str(exc))
    except Exception:
        logger.exception("Unexpected error processing image")
        return _error(500, "Internal server error")


# ---------------------------------------------------------------------------
# Bedrock orchestration
# ---------------------------------------------------------------------------

def _recognize_with_bedrock(image_bytes: bytes) -> list[list[int]]:
    """Try each model in _MODELS; return the best valid grid across all attempts.

    Scoring (higher is better):
      +2  no duplicate digits in any row/col/box
      +1  per 10 clues above the minimum threshold (rewards richer grids)

    A grid with duplicates is still returned if it's the best we got — the
    duplicate check is a quality signal, not a hard rejection.  This avoids
    surfacing 422 errors to the user when the image is recognisable but the
    model made a small mis-read.
    """
    client = boto3.client("bedrock-runtime", region_name="eu-west-2")
    best_grid: list[list[int]] | None = None
    best_score: int = -1
    last_error: Exception | None = None

    for model_id in _MODELS:
        try:
            grid = _invoke_model(client, model_id, image_bytes)
            clues = sum(v != 0 for row in grid for v in row)
            if clues < _MIN_PLAUSIBLE_CLUES:
                raise ValueError(
                    f"Grid has only {clues} filled cells — "
                    "expected at least 10 for a valid puzzle"
                )
            has_dupe = _has_row_col_box_duplicate(grid)
            score = (0 if has_dupe else 2) + (clues - _MIN_PLAUSIBLE_CLUES) // 10
            if has_dupe:
                logger.warning(
                    "Model %s: grid has duplicate digits (score=%d, clues=%d) — "
                    "keeping as candidate but trying next model",
                    model_id, score, clues,
                )
            else:
                logger.info(
                    "Model %s returned a clean grid (score=%d, clues=%d)",
                    model_id, score, clues,
                )
            if score > best_score:
                best_score = score
                best_grid = grid
            # Stop early if we have a clean, high-quality result
            if not has_dupe and clues >= 17:
                break
        except (ValueError, ClientError) as exc:
            logger.warning("Model %s failed: %s", model_id, exc)
            last_error = exc

    if best_grid is not None:
        return best_grid

    raise ValueError(
        f"All models failed to extract a valid Sudoku grid. "
        f"Last error: {last_error}"
    )


def _invoke_model(
    client: object,
    model_id: str,
    image_bytes: bytes,
) -> list[list[int]]:
    """
    Invoke a Bedrock model via the Converse API.

    The Converse API is used (rather than InvokeModel) because it supports a
    system prompt for all model families, which is critical for reliable JSON
    output from Nova models.
    """
    response = client.converse(
        modelId=model_id,
        system=[{"text": _SYSTEM_PROMPT}],
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "image": {
                            "format": "jpeg",
                            "source": {"bytes": image_bytes},
                        }
                    },
                    {"text": _USER_PROMPT},
                ],
            }
        ],
        inferenceConfig={"maxTokens": 2048, "temperature": 0},
    )

    text = response["output"]["message"]["content"][0]["text"]
    return _parse_grid(text)


# ---------------------------------------------------------------------------
# Response parsing
# ---------------------------------------------------------------------------

def _parse_grid(text: str) -> list[list[int]]:
    """
    Parse the model text response into a 9x9 list of ints.
    Handles accidental markdown fences and prose-wrapped JSON.
    Raises ValueError if no valid grid is found.
    """
    cleaned = text.strip()

    # Strip markdown code fences
    if "```" in cleaned:
        lines = cleaned.splitlines()
        cleaned = "\n".join(
            line for line in lines if not line.startswith("```")
        ).strip()

    # Try direct parse
    data = None
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        pass

    # Fall back: extract the first complete {...} object
    if data is None:
        start = cleaned.find("{")
        end = cleaned.rfind("}") + 1
        if start == -1 or end <= 0:
            raise ValueError(
                f"No JSON object found in model response: {cleaned[:200]!r}"
            )
        try:
            data = json.loads(cleaned[start:end])
        except json.JSONDecodeError as exc:
            raise ValueError(
                f"Model response contains invalid JSON: {exc}"
            ) from exc

    grid = data.get("originalGrid")
    if not isinstance(grid, list) or len(grid) != 9:
        raise ValueError(
            f"'originalGrid' must be a 9-element list, got: {type(grid)}"
        )
    for i, row in enumerate(grid):
        if not isinstance(row, list) or len(row) != 9:
            raise ValueError(f"Row {i} must be a 9-element list, got: {row!r}")
        for j, val in enumerate(row):
            if not isinstance(val, int) or not (0 <= val <= 9):
                raise ValueError(
                    f"Cell [{i}][{j}] must be an int 0-9, got: {val!r}"
                )

    return grid


# ---------------------------------------------------------------------------
# Image utilities
# ---------------------------------------------------------------------------

def _downscale_image(image_bytes: bytes) -> bytes:
    """
    Downscale the image so its longest edge is at most _MAX_IMAGE_EDGE pixels,
    then re-encode as JPEG.

    Falls back to the original bytes if Pillow is unavailable or decoding fails.
    """
    try:
        from PIL import Image  # type: ignore[import]

        img = Image.open(io.BytesIO(image_bytes))
        w, h = img.size
        if max(w, h) > _MAX_IMAGE_EDGE:
            scale = _MAX_IMAGE_EDGE / max(w, h)
            img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)

        if img.mode != "RGB":
            img = img.convert("RGB")

        # Desaturate to a neutral grey palette so the model cannot confuse cell
        # background colours (orange, yellow, blue highlights) with digit content,
        # while preserving the luminance contrast that makes digits legible.
        # Using ImageEnhance rather than a hard L→RGB round-trip retains slightly
        # more detail in low-contrast printed numerals.
        try:
            from PIL import ImageEnhance  # type: ignore[import]
            img = ImageEnhance.Color(img).enhance(0.0)   # full desaturation
            img = ImageEnhance.Contrast(img).enhance(1.4)  # boost digit contrast
            img = ImageEnhance.Sharpness(img).enhance(2.0)  # crisp digit edges
        except Exception:  # noqa: BLE001
            # Fallback: hard greyscale round-trip (original approach)
            img = img.convert("L").convert("RGB")

        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=85)
        return buf.getvalue()

    except Exception as exc:  # noqa: BLE001
        logger.warning("Image downscale failed (%s); using original bytes", exc)
        return image_bytes


def _has_row_col_box_duplicate(grid: list[list[int]]) -> bool:
    """Return True if any row, column, or 3x3 box contains a duplicate non-zero digit."""
    for i in range(9):
        row_vals = [v for v in grid[i] if v != 0]
        col_vals = [grid[r][i] for r in range(9) if grid[r][i] != 0]
        box_r, box_c = (i // 3) * 3, (i % 3) * 3
        box_vals = [
            grid[box_r + dr][box_c + dc]
            for dr in range(3) for dc in range(3)
            if grid[box_r + dr][box_c + dc] != 0
        ]
        if (len(row_vals) != len(set(row_vals))
                or len(col_vals) != len(set(col_vals))
                or len(box_vals) != len(set(box_vals))):
            return True
    return False


def _error(status_code: int, message: str) -> dict:
    return {
        "statusCode": status_code,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps({"error": message}),
    }
