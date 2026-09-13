"""
AWS Lambda Handler — Sudoku image recognition via Amazon Bedrock.

Accepts a base64-encoded image via API Gateway and returns a 9x9 Sudoku grid.
Uses the Bedrock Converse API so the model receives a proper system prompt,
which is essential for structured JSON output.

"""

from __future__ import annotations

import base64
import json
import logging
import re

from providers import ProviderError, VisionProvider, get_provider

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
if not logger.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(levelname)s %(name)s: %(message)s"))
    logger.addHandler(_handler)

# ---------------------------------------------------------------------------
# The candidate model list and region belong to the provider — see providers/. The
# recognition loop below is deliberately provider-agnostic. @spec IR-AI-006

# Recognition logic, not provider config: the scoring threshold below which a grid is
# treated as a mis-read rather than a sparse puzzle.
_MIN_PLAUSIBLE_CLUES = 10

# ---------------------------------------------------------------------------
# Prompts
# ---------------------------------------------------------------------------

# Prompts live in prompts.py, shared verbatim by every provider. @spec IR-AI-004

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

    @spec IR-API-001, IR-API-002, IR-BE-001, IR-BE-002, IR-API-010, IR-API-011, IR-API-012
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
        logger.info(
            "Received image recognition request: image_size=%d bytes", len(image_bytes)
        )

        if len(image_bytes) > 8 * 1024 * 1024:
            return _error(400, "Image too large — maximum size is 8 MB.")

        provider = get_provider()
        grid, valid, model_name = _recognize(provider, image_bytes)

        if not valid:
            logger.warning(
                "Best grid from model %s is invalid (has duplicates or too few clues) — returning 422",
                model_name,
            )
            return _error(
                422,
                "Could not extract a valid Sudoku grid from the image. Please try a clearer photo.",
            )

        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps(
                {"originalGrid": grid, "validPuzzle": valid, "modelName": model_name}
            ),
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


def _recognize(
    provider: VisionProvider, image_bytes: bytes
) -> tuple[list[list[int]], bool, str]:
    """Try each of the provider's models; return (best_grid, valid, model_name).

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

    @spec IR-PROC-030, IR-PROC-031, IR-PROC-032, IR-PROC-033
    """
    best_grid: list[list[int]] | None = None
    best_score: int = -1
    best_has_dupe: bool = True
    best_clues: int = 0
    best_model_name: str = ""
    last_error: Exception | None = None

    model_index = 0
    while model_index < len(provider.models):
        model_id = provider.models[model_index]
        try:
            logger.info("Trying model %s (index %d)", model_id, model_index)
            grid = _parse_grid(provider.recognise(image_bytes, model_id))
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
                    model_id,
                    score,
                    clues,
                )
            else:
                logger.info(
                    "Model %s returned a clean grid (score=%d, clues=%d)",
                    model_id,
                    score,
                    clues,
                )
            if score > best_score:
                best_score = score
                best_grid = grid
                best_has_dupe = has_dupe
                best_clues = clues
                best_model_name = model_id

            # When a model returns a valid result, always cross-check with the next model
            if not has_dupe and clues >= 17 and model_index + 1 < len(provider.models):
                next_model_id = provider.models[model_index + 1]
                logger.info(
                    "Model %s produced acceptable result; cross-checking with next model %s",
                    model_id,
                    next_model_id,
                )
                try:
                    next_grid = _parse_grid(
                        provider.recognise(image_bytes, next_model_id)
                    )
                    if next_grid != grid:
                        next_clues = sum(v != 0 for row in next_grid for v in row)
                        next_has_dupe = _has_row_col_box_duplicate(next_grid)
                        next_score = (0 if next_has_dupe else 2) + (
                            next_clues - _MIN_PLAUSIBLE_CLUES
                        ) // 10
                        if next_score > best_score:
                            logger.info(
                                "Next model %s produced a different grid with higher score (%d > %d); using its result instead of %s",
                                next_model_id,
                                next_score,
                                best_score,
                                model_id,
                            )
                            best_grid = next_grid
                            best_model_name = next_model_id
                            best_has_dupe = next_has_dupe
                            best_clues = next_clues
                            best_score = next_score
                        else:
                            logger.info(
                                "Next model %s produced a different grid but lower/equal score (%d <= %d); keeping result from %s",
                                next_model_id,
                                next_score,
                                best_score,
                                model_id,
                            )
                    else:
                        logger.info(
                            "Next model %s confirmed result; keeping grid from %s",
                            next_model_id,
                            model_id,
                        )
                except (ValueError, ProviderError) as exc:
                    logger.warning(
                        "Cross-check model %s failed: %s; keeping result from %s",
                        next_model_id,
                        exc,
                        model_id,
                    )
                # Skip the next model in the main loop since we already tried it
                model_index += 2
                continue

            model_index += 1

        except (ValueError, ProviderError) as exc:
            logger.warning("Model %s failed: %s", model_id, exc)
            last_error = exc
            model_index += 1

    if best_grid is not None:
        valid = not best_has_dupe and best_clues >= 17
        logger.info(
            "Final result: model=%s valid=%s clues=%d",
            best_model_name,
            valid,
            best_clues,
        )
        return best_grid, valid, best_model_name

    raise ValueError(
        f"All models failed to extract a valid Sudoku grid. Last error: {last_error}"
    )


# _detect_image_format moved to providers/ — each adapter needs it in its own
# vocabulary (Bedrock a bare format name, Vertex a MIME type). @spec IR-AI-002


# ---------------------------------------------------------------------------
# Response parsing
# ---------------------------------------------------------------------------
# --- Enhanced Robust Parser ---


def _parse_grid(text: str) -> list[list[int]]:
    """
    Enhanced parser that handles JSON and falls back to parsing
    the pipe-delimited scratchpad if the JSON is malformed.

    @spec IR-PROC-020, IR-PROC-021, IR-PROC-022, IR-PROC-023
    """
    cleaned = text.strip()

    # 1. Try to find a JSON object — prefer <json> tags, fall back to first {...} block
    json_match = re.search(
        r"<json>\s*(.*?)\s*</json>", cleaned, re.DOTALL | re.IGNORECASE
    )
    if not json_match:
        json_match = re.search(r"(\{.*\})", cleaned, re.DOTALL)

    if json_match:
        json_str = json_match.group(1).strip()
        json_str = re.sub(r"//.*", "", json_str)  # remove JS-style comments
        try:
            data = json.loads(json_str)
        except json.JSONDecodeError as exc:
            raise ValueError(
                f"Extracted string is invalid JSON: {exc}\nString: {json_str[:100]}"
            ) from exc

        grid = data.get("originalGrid")

        # FIX: Better error logging so it prints the actual length, not just <class 'list'>
        if not isinstance(grid, list) or len(grid) != 9:
            actual_len = len(grid) if isinstance(grid, list) else "N/A"
            raise ValueError(
                f"'originalGrid' must be a 9-element list, got a {type(grid).__name__} of length {actual_len}"
            )

        for i, row in enumerate(grid):
            if not isinstance(row, list) or len(row) != 9:
                raise ValueError(f"Row {i} must be a 9-element list, got: {row!r}")
            for j, val in enumerate(row):
                if isinstance(val, str) and val.isdigit():
                    val = int(val)
                    grid[i][j] = val
                if not isinstance(val, int) or not (0 <= val <= 9):
                    raise ValueError(
                        f"Cell [{i}][{j}] must be an int 0-9, got: {val!r}"
                    )

        return grid

    # 2. Fallback: Parse the pipe-delimited scratchpad
    grid_from_text = []
    for line in cleaned.splitlines():
        if "|" in line:
            # FIX: Strip out "Row 1:" prefixes before splitting
            if ":" in line:
                line = line.split(":", 1)[-1]

            parts = [p.strip() for p in line.split("|")]
            row_digits = []
            for p in parts:
                # Strip markdown bolding just in case (e.g., **5**)
                p = re.sub(r"[*_]", "", p).strip()
                if p.isdigit():
                    row_digits.append(int(p))
                elif p == "." or p.lower() == "empty":
                    row_digits.append(0)

            if len(row_digits) == 9:
                grid_from_text.append(row_digits)

    if len(grid_from_text) == 9:
        logger.info("Successfully recovered grid from scratchpad pipes.")
        return grid_from_text

    raise ValueError(
        "No JSON object found and no valid pipe-delimited scratchpad in model response."
    )


def _has_row_col_box_duplicate(grid: list[list[int]]) -> bool:
    """Return True if any row, column, or 3x3 box contains a duplicate non-zero digit."""
    for i in range(9):
        row_vals = [v for v in grid[i] if v != 0]
        col_vals = [grid[r][i] for r in range(9) if grid[r][i] != 0]
        box_r, box_c = (i // 3) * 3, (i % 3) * 3
        box_vals = [
            grid[box_r + dr][box_c + dc]
            for dr in range(3)
            for dc in range(3)
            if grid[box_r + dr][box_c + dc] != 0
        ]
        if (
            len(row_vals) != len(set(row_vals))
            or len(col_vals) != len(set(col_vals))
            or len(box_vals) != len(set(box_vals))
        ):
            return True
    return False


def _error(status_code: int, message: str) -> dict:
    return {
        "statusCode": status_code,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps({"error": message}),
    }
