# Arrow: Image Recognition

Photo-to-grid extraction via Amazon Bedrock, with image preprocessing, two-stage parser, and multi-model scoring.

## Status

**MAPPED** - 2026-04-18. handler.py fully read and documented. No tests audited yet.

## References

### HLD
- docs/high-level-design.md — "Data Flow: Import from Photo" section

### LLD
- docs/llds/image-recognition.md

### EARS
- docs/specs/image-recognition-specs.md (20 specs, all [x])

### Tests
- image_recognition/tests/ (not yet audited)

### Code
- image_recognition/handler.py
- image_recognition/requirements.txt

## Architecture

**Purpose:** Accept a base64-encoded photo of a Sudoku puzzle and return a 9×9 integer grid using Bedrock (Claude Haiku) as the OCR engine.

**Key Components:**
1. `handler()` — Lambda entry point; routes warmup probe, validates input, orchestrates pipeline
2. `_downscale_image()` — PIL preprocessing (resize, alpha removal, desaturate, JPEG encode)
3. `_invoke_model()` — Bedrock Converse API call with system prompt + chain-of-thought user prompt
4. `_parse_grid()` — two-stage parser: JSON `<json>` tags → pipe-delimited scratchpad fallback
5. `_recognize_with_bedrock()` — multi-model cascade with scoring and cross-check logic
6. `_has_row_col_box_duplicate()` — constraint validation on extracted grid

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Input Handling | IR-API-001 to 002, IR-BE-001 to 002 | 4 | 0 | 0 |
| Image Preprocessing | IR-PROC-001 to 006 | 6 | 0 | 0 |
| Bedrock Invocation | IR-PROC-010 to 012 | 3 | 0 | 0 |
| Grid Parsing | IR-PROC-020 to 023 | 4 | 0 | 0 |
| Validation & Scoring | IR-PROC-030 to 033 | 4 | 0 | 0 |
| Response | IR-API-010 to 012 | 3 | 0 | 0 |

**Summary:** 24 of 24 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Single model in cascade** — `_MODELS` contains only one entry (Claude Haiku). The cross-check and scoring logic is fully implemented but untested with multiple live models.
2. **IAM vs code mismatch** — IAM policy grants `bedrock:InvokeModel` on 5 model ARNs (Nova Pro, Nova Lite, Mistral, Nemotron, Claude Haiku). Only Claude Haiku appears in `_MODELS`. (IR-PROC-010)
3. **`AWS_REGION_NAME` defaults to us-east-1** — The rest of the system runs in eu-west-2. If the env var is not injected, Bedrock calls go to the wrong region. Terraform injects it correctly, but the default is a silent footgun.
4. **PIL fallback is silent** — If PIL processing fails, original bytes are sent to Bedrock with no error signal to the caller. The model may receive a larger, colour image. (IR-PROC-005)
5. **Warmup path matching** — The handler checks `"/warmup" in event["path"]` rather than an exact path match. Any path containing "/warmup" would be intercepted.

## Work Required

### Must Fix
1. Change `AWS_REGION_NAME` default from "us-east-1" to "eu-west-2" to eliminate the silent wrong-region footgun.

### Should Fix
2. Sync IAM policy model ARNs with the actual `_MODELS` list, or document that the IAM grants are intentionally broad for future use.

### Nice to Have
3. Add a second model to `_MODELS` to exercise the cross-check logic under real conditions.
4. Use exact path matching for the warmup probe rather than substring match.
