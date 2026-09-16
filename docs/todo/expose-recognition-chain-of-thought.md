# Expose the recognition chain of thought

**Summary:** The model's reasoning is produced and paid for on every request, then thrown away — surfacing it would make mis-reads diagnosable instead of mysterious.

**Branch context:** `image-recognition-thinking-tokens` — Phase 5 of the GCP Pulumi re-platform, where measuring token spend showed how much reasoning is being discarded.

## Why deferred

Came out of the cost analysis rather than a feature request, so it has had no design pass and no EARS specs. It also changes the API response shape, which is a User Management / React Frontend concern as much as an image-recognition one.

## Context

**Relevant files:**
- `image_recognition/prompts.py` — `USER_PROMPT` instructs the model to transcribe the grid into a `<scratchpad>` before emitting JSON
- `image_recognition/handler.py` — `_parse_grid` reads the `<json>` block, falling back to parsing the scratchpad pipes when the JSON is malformed; the scratchpad is never returned
- `image_recognition/handler.py:110` — the 200 response body: `{originalGrid, validPuzzle, modelName}`
- `image_recognition/providers/vertex.py` — `_thinking_config`; `types.ThinkingConfig` also exposes `include_thoughts`
- `ui/src/api/sudokuApi.js` — the frontend caller

**Current state:**
Two separate streams of reasoning exist and both are discarded. The **scratchpad** is a visible, prompt-mandated transcription that `_parse_grid` uses only as a fallback and never surfaces. Separately, Gemini produces **hidden thinking tokens** that cannot be switched off — measured at roughly 60% of Vertex's output spend, about $0.0033 per image — and which the SDK can return via `ThinkingConfig(include_thoughts=True)` but currently does not. So the service already pays for reasoning on every request and shows the user none of it. When a grid comes back with one wrong digit, there is no way to tell whether the model misread the cell or mis-aligned the column.

**Key constraints:**
- `IR-AI-004` — both providers must use byte-identical prompts. Any prompt change to improve the scratchpad applies to both, or needs an explicit exception.
- Bedrock has no equivalent of `include_thoughts`; Claude's reasoning here *is* the scratchpad. Any response field must degrade cleanly rather than imply Vertex-only behaviour.
- Response shape is consumed by `ui/` and by `backend`'s `/games/from-image` path — adding a field is safe, changing existing ones is not.
- The scratchpad can be long. Returning it unconditionally inflates every response; consider a query flag or dev-tools gating (`VITE_DEV_TOOLS` already gates developer UI).

## What to do

1. Decide the scope first: a **debugging aid** (returned only when asked for, shown behind dev tools) or a **user-facing explanation** (always returned, rendered in the import dialog). These have very different design and privacy implications. Recommend starting with the former.
2. Capture the scratchpad in `_parse_grid` rather than discarding it — return it alongside the grid instead of only using it as a fallback.
3. Add it to the 200 response as an optional field, e.g. `reasoning: {scratchpad, thoughts?}`, absent unless requested.
4. For Vertex, evaluate `ThinkingConfig(include_thoughts=True)` — the thought tokens are already being billed, so exposing them is free. Confirm whether it changes `candidates_token_count` accounting before assuming so.
5. Add EARS specs under `IR-API-*` for the response field and `IR-AI-*` for provider-side capture, and walk the LID cascade — this is a behavioural change, not a refactor.
6. If surfacing in the UI, the import dialog in `ui/src/` is the natural home; gate it on `VITE_DEV_TOOLS` initially.

## Acceptance criteria

- [ ] A mis-read fixture can be diagnosed from the response alone — column mis-alignment distinguishable from digit mis-read
- [ ] The default response shape is unchanged, so existing `ui/` and backend callers are unaffected
- [ ] Works on both providers, with Bedrock degrading to scratchpad-only rather than erroring
- [ ] Token accounting is re-measured if `include_thoughts` is enabled — confirm it is genuinely free
- [ ] EARS specs added and the arrow updated

## Related specs / docs

- [`docs/specs/image-recognition-specs.md`](../specs/image-recognition-specs.md) — `IR-API-010..012` (response), `IR-PROC-012/013` (prompts), `IR-AI-004` (shared prompts)
- [`docs/llds/image-recognition.md`](../llds/image-recognition.md) — two-stage parser and cross-check scoring
- [`docs/planning/gcp-pulumi-phase-5-image-recognition-vertex.md`](../planning/gcp-pulumi-phase-5-image-recognition-vertex.md) §5 — thinking-token measurements
