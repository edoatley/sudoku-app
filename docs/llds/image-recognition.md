# Image Recognition

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Image Recognition component accepts a photo of a Sudoku puzzle and returns a 9×9 grid of digits. It runs as a separate AWS Lambda function written in Python 3.14, invoked via API Gateway. It uses Amazon Bedrock's Converse API to call Claude Haiku as the OCR engine, with image preprocessing to improve model accuracy and reduce token cost.

Files: `image_recognition/handler.py`, `image_recognition/requirements.txt`.

This component is entirely independent of the Java backend — it has its own Lambda function, IAM role, and API Gateway routes. The Java `GameResource` never calls it directly; the frontend calls it and passes the result to `POST /api/v1/games/from-image`.

## Request Flow

```text
Frontend (ImportModal)
  → base64-encode image file
  → POST /api/v1/ai/scan  {image: "<base64>"}  (JWT required)
        │
        ▼
  API Gateway → Image Recognition Lambda (Python)
        │
        ▼
  handler(event, context)
    1. Parse & validate request
    2. Decode base64 → bytes
    3. Preprocess image (resize, desaturate, alpha)
    4. Call Bedrock Converse API (Claude Haiku)
    5. Parse model response (JSON tags → pipe table fallback)
    6. Validate grid (duplicates, clue count)
    7. Return {originalGrid, validPuzzle, modelName}
        │
        ▼
  Frontend receives grid → displays for review
  → POST /api/v1/games/from-image  {originalGrid: [[...]]}
        │
        ▼
  Java GameService (3-stage validation + persist)
```

## Image Preprocessing

Before sending to Bedrock, the image is preprocessed via PIL (Pillow). If PIL is unavailable (missing Lambda layer), the original bytes are sent as-is.

| Step | What | Why |
| --- | --- | --- |
| Resize | Downscale to max 800px on longest edge (LANCZOS) | Reduces image token cost; Bedrock charges per pixel |
| Alpha removal | Composite RGBA/LA/P onto white background | Transparent regions appear as black to some models; white background is neutral |
| Desaturate | Convert to greyscale (PIL ImageEnhance.Color(0.0)) | Removes color bias; digit recognition is shape-based |
| JPEG encode | Save at quality=85 | Further reduces token size; JPEG is efficient for photos |

Format detection uses magic-byte signatures (PNG `\x89PNG`, JPEG `\xff\xd8\xff`, GIF `GIF87a`/`GIF89a`, WebP `RIFF...WEBP`). Unknown formats default to JPEG.

Maximum accepted image size: **8 MB** (checked before preprocessing).

## Bedrock Integration

### Model Cascade

```python
_MODELS = [
    "eu.anthropic.claude-haiku-4-5-20251001-v1:0",
]
```

The model list is injected via the `BEDROCK_MODELS` env var (comma-separated), set by Terraform from `local.bedrock_models`. The same list drives the IAM policy, so IAM and code are always in sync. The hardcoded value above is the fallback for local/test runs only.

Comparison testing (2026-04-20) against 5 ground-truth fixtures evaluated Haiku 4.5 and Nemotron Nano 12B v2, with and without PIL preprocessing. Haiku 4.5 without PIL was the only configuration achieving 100% cell accuracy (5/5 exact matches). PIL degraded accuracy on colour-coded puzzles; Nemotron achieved 1/5 exact matches. Single-model is the correct configuration.

### Prompts

**System prompt:**
> "You are a precise Sudoku digit extractor. You specialize in spatial mapping. You count columns from left to right (1-9) and rows from top to bottom (1-9). You never skip a cell, even if it is empty. You use visual anchors to stay aligned. Some puzzles have coloured or shaded cell backgrounds (orange, yellow, tan, grey). Ignore background colour entirely — read only the digit if one is printed in the cell; if no digit is visible, the cell is empty regardless of its background colour."

The colour-cell instruction (IR-PROC-013) addresses two related failure modes observed with puzzle_5.png:

1. The model misread shaded empty cells as containing digits (hallucination from background colour).
2. The model skipped shaded-but-empty cells when counting columns, shifting remaining digits in the row left by one position — causing duplicates in the extracted grid.

The user prompt fix explicitly states that shaded cells still occupy their column position and each row must have exactly 9 pipe-separated values in the scratchpad. The system prompt adds a general colour-cell rule. Together these resolved puzzle_5 from a consistent 422 (duplicate grid) to a perfect 25/25 cell match. Changes are additive and do not affect black-on-white puzzles. PIL desaturation remains deferred.

**User prompt:** Asks the model to:

1. Transcribe the grid in a `<scratchpad>` using a pipe-delimited table (`.` for empty cells)
2. Verify every row has exactly 9 cells
3. Output JSON in `<json>` tags with key `originalGrid`, using `0` for empty cells

The scratchpad step is a deliberate chain-of-thought technique — having the model align the grid visually before producing JSON reduces positional errors.

**Inference config:** `maxTokens=2048`, `temperature=0` (deterministic output).

The Converse API (not InvokeModel) is used because it supports a `system` parameter, which is critical for reliable JSON output from Claude models.

## Grid Parsing — Two-Stage Parser

`_parse_grid(text)` extracts the grid from the model's response:

**Stage 1 — JSON extraction (preferred):**

1. Search for `<json>...</json>` tags
2. Fall back to first `{...}` block if tags missing
3. Strip JS-style `//` comments
4. Parse JSON, validate:
   - `originalGrid` key exists
   - Exactly 9 rows × 9 columns
   - All values are integers 0–9 (string digits coerced)

**Stage 2 — Pipe-delimited scratchpad fallback:**

If JSON parsing fails entirely, parse the scratchpad:

- Find lines containing `|`
- Strip "Row N:" prefixes
- Split by `|`, parse each cell (`.` or `empty` → 0, digit → int, strips markdown bold)
- Accept rows with exactly 9 digits
- Return if 9 complete rows recovered

This fallback handles cases where the model omits the JSON block but produces a correct scratchpad.

## Grid Validation & Scoring

After parsing, each candidate grid is scored:

| Condition | Score contribution |
| --- | --- |
| No row/col/box duplicates | +2 |
| Per 10 clues above `_MIN_PLAUSIBLE_CLUES` (10) | +1 |

Example: 25 clues, no duplicates → score = 2 + (25−10)÷10 = 3

Duplicate detection (`_has_row_col_box_duplicate`) checks all 27 units (9 rows, 9 cols, 9 blocks). Empty cells (0) are ignored.

Thresholds:

| Check | Value | Meaning |
| --- | --- | --- |
| `_MIN_PLAUSIBLE_CLUES` | 10 | Below this: grid is too sparse to be a real puzzle scan, skip model result |
| Valid puzzle minimum | 17 | Standard minimum for a uniquely-solvable Sudoku; `validPuzzle=true` only if ≥17 clues and no duplicates |

## Multi-Model Cross-Check Logic

The cascade logic handles multiple models (currently one, infrastructure ready for more):

```text
for each model in _MODELS:
  try:
    grid = invoke_model(model)
    clues = count non-zero cells
    if clues < 10 → skip (too sparse)
    has_dupe = check duplicates
    score = 2 if no_dupe else 0 + bonus

    if no_dupe AND clues >= 17:
      if next model exists:
        invoke next model for cross-check
        if grids differ → use higher-scored result
        if grids match → confirmed, return
      else:
        return immediately (only model, result trusted)

    if score > best_score:
      best = (grid, has_dupe, clues, model_name)

  except (ValueError, ClientError):
    log warning, try next model

if best found:
  return (grid, valid=(not has_dupe AND clues>=17), model_name)
else:
  raise ValueError("all models failed")
```

Key design: a grid with duplicates is **not rejected outright** if it is the best result across all models. The `validPuzzle=false` flag signals to the frontend/backend that the extraction may be imperfect — the Java `GameService` will still run its own validation and reject truly unsolvable grids.

## HTTP Interface

| Route | Method | Auth | Request | Response |
| --- | --- | --- | --- | --- |
| `/api/v1/ai/scan` | POST | JWT required | `{"image": "<base64>"}` | see below |
| `/api/v1/ai/scan/warmup` | GET | None | — | 200 (probe only) |

**Success response (200):**

```json
{
  "originalGrid": [[5,3,0,...], ...],
  "validPuzzle": true,
  "modelName": "eu.anthropic.claude-haiku-4-5-20251001-v1:0"
}
```

**Error responses:**

| Status | Condition |
| --- | --- |
| 400 | Missing/empty image field, invalid JSON, image > 8 MB |
| 422 | Grid invalid (duplicates present and < 17 clues) or all models failed |
| 500 | Unexpected internal error |

The warmup route (`GET /api/v1/ai/scan/warmup`) returns 200 immediately without invoking Bedrock. The frontend calls it on profile load (when import is enabled) to reduce cold-start latency for the first real scan.

## Infrastructure

- **Runtime:** Python 3.14, container image (ECR)
- **Memory:** 512 MB
- **Timeout:** 60 seconds (Bedrock inference ~20s + cold start ~20s)
- **IAM:** `bedrock:InvokeModel` on Claude Haiku + Nova Pro + Nova Lite + Mistral + Nemotron ARNs (IAM pre-grants multiple models even though only Haiku is in the current code cascade)
- **No SnapStart:** Python Lambda; cold start managed via warmup probe
- **ECR:** Shared repository `sudoku-image-recognition` across all workspaces, tagged `{branch}-{sha}` and `{branch}-latest`

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Separate Python Lambda | Independent function from Java backend | Add Python endpoint to Quarkus | Bedrock SDK is Pythonic; Pillow requires native binaries incompatible with Java runtime; clean separation |
| Container image (not ZIP) | ECR container image | Lambda ZIP with layer | Pillow requires compiled C extensions; container avoids layer management complexity |
| Converse API over InvokeModel | `bedrock.converse()` | `bedrock.invoke_model()` | Converse API supports `system` parameter natively; system prompt is essential for JSON reliability |
| Chain-of-thought scratchpad | Pipe-delimited scratchpad before JSON | JSON directly | Reduces positional errors; model "thinks" through alignment before committing to structured output |
| Fuzzy return (duplicates allowed) | Return best grid with `validPuzzle=false` | Reject all grids with duplicates | Better UX: Java backend runs own validation; user sees extracted grid even if imperfect |
| Warmup probe | `GET /api/v1/puzzles/import/warmup` (no auth) | Scheduled EventBridge ping | Frontend-initiated; warms the specific Lambda; no infrastructure overhead |
| Desaturate before sending | `ImageEnhance.Color(0.0)` | Send colour image | Sudoku digits are shape-based; removing colour reduces irrelevant visual features |
| Static colour-cell hint | One sentence in system prompt (IR-PROC-013) | Adaptive pixel-based prompt injection | Zero latency, zero dependency; model already handles most colour puzzles correctly — explicit instruction makes it reliable. Adaptive approach deferred as future optimisation. |
| Temperature 0 | `inferenceConfig: {temperature: 0}` | Default temperature | Digit extraction should be deterministic; randomness only adds errors |

## Technical Debt & Inconsistencies

- `_MODELS` contains only one entry. The multi-model cross-check code remains in place but is not exercised. It can be activated by adding a second model to `local.bedrock_models` in Terraform — but only after validating accuracy against the ground-truth fixtures in `tests/e2e_config.json`.
- PIL preprocessing (`_downscale_image`) is implemented and unit-tested but the call in `handler()` is deferred. E2e testing (2026-04-20) showed that PIL desaturation causes orange-highlighted cells to be misread — the colour cue for emptiness is lost. A static colour-cell hint (IR-PROC-013) was added to the system prompt as the first mitigation. Re-enable PIL only after confirming it does not degrade colour-puzzle accuracy.
- `AWS_REGION_NAME` defaults to `eu-west-2`; Terraform also injects it explicitly via the Lambda environment block.
- Error logging uses `print`-style statements in some places alongside the configured logger. Inconsistent logging approach.
- The `handler()` function is 70+ lines long and handles parsing, validation, and response building inline. Extracting `_parse_request()` and `_build_response()` helpers would improve readability.

## Behavioral Quirks

- The warmup route matches on `event["path"]` containing `/warmup`, not an exact match. Any path containing the string `/warmup` would be intercepted — though only `/api/v1/puzzles/import/warmup` is routed to this Lambda.
- PIL preprocessing silently falls back to original bytes on any exception (including missing PIL). The model then receives the raw image — larger and possibly in a format that increases token cost without error.
- `validPuzzle=false` in the 200 response does not cause the frontend to reject the grid — it flags it for extra validation downstream. The Java `GameService` is the final authority on puzzle validity.
- Cross-check logic skips the next model in the main loop after using it for cross-check (to avoid redundant invocation). With only one model, this branch is never reached.

## References

- `image_recognition/handler.py`
- `image_recognition/requirements.txt`
- `infra/image_recognition_lambda.tf`
- `infra/api_gateway.tf` (import routes)
- `ui/src/api/sudokuApi.js` (`importPuzzle`, `warmupImageRecognition`)
- `ui/src/components/ImportModal.jsx`
- Depends on: Amazon Bedrock (external), PIL/Pillow (Lambda layer/container)
- Depended on by: Frontend (ImportModal → sudokuApi.importPuzzle)
