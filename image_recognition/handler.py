"""
AWS Lambda Handler — Sudoku image recognition via Amazon Bedrock.

Accepts a base64-encoded image via API Gateway and returns a 9x9 Sudoku grid.
Uses the Bedrock Converse API so the model receives a proper system prompt,
which is essential for structured JSON output.

"""
from __future__ import annotations

import base64
import io
import os
import re
import json
import logging

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
if not logger.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(levelname)s %(name)s: %(message)s"))
    logger.addHandler(_handler)

# ---------------------------------------------------------------------------
# Model — populated from the BEDROCK_MODELS env var (comma-separated) injected
# by Terraform, which also generates the matching IAM policy from the same list.
# The fallback keeps local/test runs working without any env configuration.
# ---------------------------------------------------------------------------
_MODELS_DEFAULT = "eu.anthropic.claude-haiku-4-5-20251001-v1:0"
_MODELS = [m.strip() for m in os.environ.get("BEDROCK_MODELS", _MODELS_DEFAULT).split(",") if m.strip()]

# A valid Sudoku has at least 17 clues.  We use a lower threshold so that
# very sparse / near-empty grids trigger a retry with the next model.
_MIN_PLAUSIBLE_CLUES = 10

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

_DEFAULT_REGION = "eu-west-2"
_AWS_REGION = os.environ.get("AWS_REGION_NAME", _DEFAULT_REGION)

# ---------------------------------------------------------------------------
# Prompts
# ---------------------------------------------------------------------------

_SYSTEM_PROMPT = (
    "You are a precise Sudoku digit extractor. You specialize in spatial mapping. "
    "You count columns from left to right (1-9) and rows from top to bottom (1-9). "
    "You never skip a cell, even if it is empty. You use visual anchors to stay aligned."
)

_USER_PROMPT = (
    "Analyze the image of the Sudoku puzzle.\n\n"
    "1. In <scratchpad>, transcribe the grid using a pipe-delimited table. Use '.' for empty cells.\n"
    "   Example: | 5 | . | . | | . | 2 | . | | . | . | 8 |\n"
    "2. Ensure every row has exactly 9 cells and the 3x3 blocks align vertically.\n"
    "3. Output the final result as JSON in <json> tags with the key 'originalGrid'.\n\n"
    "CRITICAL: You MUST wrap your final JSON in <json> and </json> tags. Do not use standard markdown code blocks. Output 0 for empty cells."
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
        "body": "{\"originalGrid\": [[0,0,...], ...], \"validPuzzle\": true, \"modelName\": \"model-id\"}"
      }

    where ``originalGrid`` is a 9x9 list of ints (0 = empty cell).
    """
    # Warmup probe — returns immediately without invoking Bedrock
    raw_path = event.get("rawPath", "")
    if raw_path.endswith("/warmup"):
        logger.info("Warmup probe received")
        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"status": "ok", "service": "sudoku-image-recognition"}),
        }

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
        logger.info("Received image recognition request: image_size=%d bytes", len(image_bytes))

        if len(image_bytes) > 8 * 1024 * 1024:
            return _error(400, "Image too large — maximum size is 8 MB.")

        client = boto3.client("bedrock-runtime", region_name=_AWS_REGION)
        grid, valid, model_name = _recognize_with_bedrock(client, image_bytes)

        if not valid:
            logger.warning(
                "Best grid from model %s is invalid (has duplicates or too few clues) — returning 422",
                model_name,
            )
            return _error(422, "Could not extract a valid Sudoku grid from the image. Please try a clearer photo.")

        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"originalGrid": grid, "validPuzzle": valid, "modelName": model_name}),
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

def _recognize_with_bedrock(client: object, image_bytes: bytes) -> tuple[list[list[int]], bool, str]:
    """Try each model in _MODELS; return (best_grid, valid, model_name).

    Scoring (higher is better):
      +2  no duplicate digits in any row/col/box
      +1  per 10 clues above the minimum threshold (rewards richer grids)

    Whenever a model returns a valid result (no duplicates, ≥17 clues), the
    next model in the list is also tried.  If the two grids differ, the next
    model's result is used and the substitution is logged.  This cross-check
    catches cases where a higher-ranked model made a subtle mis-read that a
    lower-ranked model gets right.

    A grid with duplicates is still returned if it's the best we got — the
    duplicate check is a quality signal, not a hard rejection.  This avoids
    surfacing 422 errors to the user when the image is recognisable but the
    model made a small mis-read.

    ``valid`` is True when the best grid has no duplicate digits and has at
    least 17 clues (the minimum for a uniquely-solvable Sudoku puzzle).
    """
    best_grid: list[list[int]] | None = None
    best_score: int = -1
    best_has_dupe: bool = True
    best_clues: int = 0
    best_model_name: str = ""
    last_error: Exception | None = None

    model_index = 0
    while model_index < len(_MODELS):
        model_id = _MODELS[model_index]
        try:
            logger.info("Trying model %s (index %d)", model_id, model_index)
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
                best_has_dupe = has_dupe
                best_clues = clues
                best_model_name = model_id

            # When a model returns a valid result, always cross-check with the next model
            if not has_dupe and clues >= 17 and model_index + 1 < len(_MODELS):
                next_model_id = _MODELS[model_index + 1]
                logger.info(
                    "Model %s produced acceptable result; cross-checking with next model %s",
                    model_id, next_model_id,
                )
                try:
                    next_grid = _invoke_model(client, next_model_id, image_bytes)
                    if next_grid != grid:
                        next_clues = sum(v != 0 for row in next_grid for v in row)
                        next_has_dupe = _has_row_col_box_duplicate(next_grid)
                        next_score = (0 if next_has_dupe else 2) + (next_clues - _MIN_PLAUSIBLE_CLUES) // 10
                        if next_score > best_score:
                            logger.info(
                                "Next model %s produced a different grid with higher score (%d > %d); using its result instead of %s",
                                next_model_id, next_score, best_score, model_id,
                            )
                            best_grid = next_grid
                            best_model_name = next_model_id
                            best_has_dupe = next_has_dupe
                            best_clues = next_clues
                            best_score = next_score
                        else:
                            logger.info(
                                "Next model %s produced a different grid but lower/equal score (%d <= %d); keeping result from %s",
                                next_model_id, next_score, best_score, model_id,
                            )
                    else:
                        logger.info(
                            "Next model %s confirmed result; keeping grid from %s",
                            next_model_id, model_id,
                        )
                except (ValueError, ClientError) as exc:
                    logger.warning(
                        "Cross-check model %s failed: %s; keeping result from %s",
                        next_model_id, exc, model_id,
                    )
                # Skip the next model in the main loop since we already tried it
                model_index += 2
                continue

            model_index += 1

        except (ValueError, ClientError) as exc:
            logger.warning("Model %s failed: %s", model_id, exc)
            last_error = exc
            model_index += 1

    if best_grid is not None:
        valid = not best_has_dupe and best_clues >= 17
        logger.info("Final result: model=%s valid=%s clues=%d", best_model_name, valid, best_clues)
        return best_grid, valid, best_model_name

    raise ValueError(
        f"All models failed to extract a valid Sudoku grid. "
        f"Last error: {last_error}"
    )


def _detect_image_format(image_bytes: bytes) -> str:
    """Detect image format from magic bytes. Returns a Bedrock-compatible format string."""
    if image_bytes[:8] == b'\x89PNG\r\n\x1a\n':
        return "png"
    if image_bytes[:3] == b'\xff\xd8\xff':
        return "jpeg"
    if image_bytes[:6] in (b'GIF87a', b'GIF89a'):
        return "gif"
    if image_bytes[:4] == b'RIFF' and image_bytes[8:12] == b'WEBP':
        return "webp"
    return "jpeg"  # default fallback


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
    image_format = _detect_image_format(image_bytes)
    response = client.converse(
        modelId=model_id,
        system=[{"text": _SYSTEM_PROMPT}],
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "image": {
                            "format": image_format,
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
# --- Enhanced Robust Parser ---

def _parse_grid(text: str) -> list[list[int]]:
    """
    Enhanced parser that handles JSON and falls back to parsing
    the pipe-delimited scratchpad if the JSON is malformed.
    """
    cleaned = text.strip()

    # 1. Try to find a JSON object — prefer <json> tags, fall back to first {...} block
    json_match = re.search(r'<json>\s*(.*?)\s*</json>', cleaned, re.DOTALL | re.IGNORECASE)
    if not json_match:
        json_match = re.search(r'(\{.*\})', cleaned, re.DOTALL)

    if json_match:
        json_str = json_match.group(1).strip()
        json_str = re.sub(r'//.*', '', json_str)  # remove JS-style comments
        try:
            data = json.loads(json_str)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Extracted string is invalid JSON: {exc}\nString: {json_str[:100]}") from exc

        grid = data.get("originalGrid")
        
        # FIX: Better error logging so it prints the actual length, not just <class 'list'>
        if not isinstance(grid, list) or len(grid) != 9:
            actual_len = len(grid) if isinstance(grid, list) else 'N/A'
            raise ValueError(f"'originalGrid' must be a 9-element list, got a {type(grid).__name__} of length {actual_len}")

        for i, row in enumerate(grid):
            if not isinstance(row, list) or len(row) != 9:
                raise ValueError(f"Row {i} must be a 9-element list, got: {row!r}")
            for j, val in enumerate(row):
                if isinstance(val, str) and val.isdigit():
                    val = int(val)
                    grid[i][j] = val
                if not isinstance(val, int) or not (0 <= val <= 9):
                    raise ValueError(f"Cell [{i}][{j}] must be an int 0-9, got: {val!r}")

        return grid

    # 2. Fallback: Parse the pipe-delimited scratchpad
    grid_from_text = []
    for line in cleaned.splitlines():
        if '|' in line:
            # FIX: Strip out "Row 1:" prefixes before splitting
            if ':' in line:
                line = line.split(':', 1)[-1]
                
            parts = [p.strip() for p in line.split('|')]
            row_digits = []
            for p in parts:
                # Strip markdown bolding just in case (e.g., **5**)
                p = re.sub(r'[*_]', '', p).strip()
                if p.isdigit():
                    row_digits.append(int(p))
                elif p == '.' or p.lower() == 'empty':
                    row_digits.append(0)
            
            if len(row_digits) == 9:
                grid_from_text.append(row_digits)

    if len(grid_from_text) == 9:
        logger.info("Successfully recovered grid from scratchpad pipes.")
        return grid_from_text

    raise ValueError("No JSON object found and no valid pipe-delimited scratchpad in model response.")



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
