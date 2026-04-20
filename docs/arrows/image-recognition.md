# Arrow: Image Recognition

Photo-to-grid extraction via Amazon Bedrock (single Haiku 4.5 model), two-stage parser, and grid validation.

## Status

**OK** - 2026-04-20. handler.py and tests fully audited. All active specs implemented (19/24); IR-PROC-001–005 deferred — PIL and multi-model cascade empirically tested and rejected; single Haiku 4.5 without PIL is the only configuration achieving 100% accuracy across all fixtures. No gaps.

## References

### HLD

- docs/high-level-design.md — "Data Flow: Import from Photo" section

### LLD

- docs/llds/image-recognition.md

### EARS

- docs/specs/image-recognition-specs.md (24 specs: 19 [x], 5 [D])

### Tests

- image_recognition/tests/test_unit.py — unit tests covering all active specs
- image_recognition/tests/test_e2e_bedrock.py — live Bedrock integration tests (require AWS credentials)

### Code

- image_recognition/handler.py
- image_recognition/requirements.txt

## Architecture

**Purpose:** Accept a base64-encoded photo of a Sudoku puzzle and return a 9×9 integer grid using Bedrock (Claude Haiku 4.5) as the OCR engine.

**Key Components:**

1. `handler()` — Lambda entry point; routes warmup probe, validates input, orchestrates pipeline
2. `_invoke_model()` — Bedrock Converse API call with system prompt + chain-of-thought user prompt
3. `_parse_grid()` — two-stage parser: JSON `<json>` tags → pipe-delimited scratchpad fallback
4. `_recognize_with_bedrock()` — single-model invocation with clue-count validation and scoring
5. `_has_row_col_box_duplicate()` — constraint validation on extracted grid

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Input Handling | IR-API-001 to 002, IR-BE-001 to 002 | 4 | 0 | 0 |
| Image Preprocessing | IR-PROC-001 to 006 | 1 | 5 | 0 |
| Bedrock Invocation | IR-PROC-010 to 012 | 3 | 0 | 0 |
| Grid Parsing | IR-PROC-020 to 023 | 4 | 0 | 0 |
| Validation & Scoring | IR-PROC-030 to 033 | 4 | 0 | 0 |
| Response | IR-API-010 to 012 | 3 | 0 | 0 |

**Summary:** 19 of 24 specs implemented; 5 deferred (IR-PROC-001–005, PIL preprocessing); 0 gaps.

## Key Findings

1. **Single model, no PIL** — Comparison testing (2026-04-20) evaluated Haiku 4.5 and Nemotron Nano 12B v2, with and without PIL preprocessing, across 5 ground-truth fixtures. Haiku 4.5 without PIL was the only configuration achieving 100% cell accuracy (5/5 exact matches). Nemotron achieved 1/5; PIL degraded Haiku 4.5 from 5/5 to 2/5. Multi-model cascade code removed; PIL call removed.
2. **IAM vs code sync** — `local.bedrock_models` in Terraform is the single source of truth; IAM `Resource` list and the Lambda `BEDROCK_MODELS` env var are both derived from it.
3. **`AWS_REGION_NAME` defaults to eu-west-2** — Model uses `eu.` inference profile for EU data residency.
4. **PIL deferred** — IR-PROC-001–005 are `[D]`. PIL desaturation removes the orange colour that identifies highlighted/selected cells as empty (puzzle_2 cell [0][1] becomes `1` instead of `0`). Re-enable only after solving the colour-cell problem.
5. **Warmup uses `rawPath.endswith("/warmup")`** — exact suffix match; covered by test.

## Work Required

None — all active specs are implemented and tested.

### Deferred

- **IR-PROC-001–005 (PIL preprocessing)** — Re-enable only after solving the colour-cell desaturation problem. Options: skip desaturation step (resize + alpha only), or detect colour-highlighted cells and preserve them before greyscaling.
- **Warmup exact path match** — `endswith("/warmup")` is functionally equivalent given the routing, but an exact match would be more explicit.
