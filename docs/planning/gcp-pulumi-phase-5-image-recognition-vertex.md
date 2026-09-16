# Implementation Plan: GCP Pulumi — Phase 5, De-Bedrock Image Recognition

**Status**: Complete — 2026-09-16. **Vertex passes the cutover gate on `gemini-3.8-flash`.**
**Created**: 2026-09-13
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4
**Arrow**: `image-recognition` (`docs/arrows/image-recognition.md`)

---

## 1. Goal

Give image recognition a Vertex AI (Gemini vision) path behind a provider switch, so the GCP
deployment stops calling AWS Bedrock. This is the **last AWS runtime dependency** in the GCP
serving path: today `image_recognition` calls Bedrock unconditionally using a long-lived AWS
access key in Secret Manager. The coach escaped to Vertex in PR #212; image recognition did not,
which is why `CP-GCP-085` cannot yet be retired.

**Pure application work.** No infrastructure, no Pulumi, no cloud resources — which is why this
phase runs in parallel with 1–4 and touches nothing they touch.

AWS keeps Bedrock. This adds a second adapter; it does not remove the first.

## 2. Shape — mirror the coach, do not invent

The backend already solved this exact problem: `CoachAiClient` is a port with `BedrockCoachClient`
and `VertexCoachClient` behind it, selected at runtime by `coach.ai.provider`. Follow it.

```
image_recognition/
├── handler.py            # keeps routing, validation, scoring/cross-check, _parse_grid, _error
├── prompts.py            # NEW — system + user prompt, shared verbatim by both adapters
└── providers/
    ├── __init__.py       # VisionProvider protocol, ProviderError, get_provider()
    ├── bedrock.py        # _invoke_model moved here, ClientError wrapped as ProviderError
    └── vertex.py         # Gemini via google-genai, ADC auth
```

**The seam is `_invoke_model`** (`handler.py:329`), not `_recognize_with_bedrock`
(`handler.py:169`). That second function is the scoring and cross-check loop —
provider-agnostic already, and the most valuable tested logic in the module. It takes a `client`
and a `model_id`; it becomes `_recognize(provider, image_bytes)` and is otherwise **untouched**.

```python
def get_provider() -> VisionProvider:
    p = os.environ.get("IMAGE_AI_PROVIDER", "bedrock").lower()
    if p == "vertex":
        from .vertex import VertexVisionProvider
        return VertexVisionProvider()
    if p == "bedrock":
        from .bedrock import BedrockVisionProvider
        return BedrockVisionProvider()
    raise ValueError(f"Unknown IMAGE_AI_PROVIDER: {p!r}")   # fail loud, never silently fall back
```

The lazy import inside each branch mirrors the backend's `@LookupIfProperty`: the Lambda image
never loads `google-genai`, and Cloud Run never constructs a boto3 client.

## 3. Work items

- [x] **5a. Extract `prompts.py`** — move `_SYSTEM_PROMPT` and `_USER_PROMPT` out of `handler.py`
      unchanged. Both adapters import them, so a provider swap cannot alter recognition by
      altering the prompt.
      @spec IR-AI-004
- [x] **5b. `providers/__init__.py`** — `VisionProvider` protocol (`name`, `models`,
      `recognise(image_bytes, model_id)`), `ProviderError`, and `get_provider()`.
      @spec IR-AI-001
- [x] **5c. `providers/bedrock.py`** — move `_invoke_model` verbatim; wrap `ClientError` in
      `ProviderError`. `BEDROCK_MODELS` keeps its name, so `infra/aws` needs no change at all.
      @spec IR-AI-005, IR-AI-006
- [x] **5d. `providers/vertex.py`** — `google-genai` with `vertexai=True`, ADC credentials,
      `gemini-2.5-flash`. Full `flash`, not the coach's `flash-lite`: grid OCR is materially
      harder than text generation. Map `_detect_image_format`'s Bedrock format string to a MIME
      type locally rather than changing the shared function.
      @spec IR-AI-002, IR-AI-006
- [x] **5e. Rework the loop** — `_recognize_with_bedrock` → `_recognize(provider, image_bytes)`;
      `except (ValueError, ClientError)` → `except (ValueError, ProviderError)`. **No change to
      the scoring, cross-check, or duplicate-detection logic.**
      @spec IR-AI-003, IR-AI-005
- [x] **5f. Requirements** — new `requirements-vertex.txt` (pinned `google-genai`);
      `requirements-cloudrun.txt` includes it. `requirements.txt` (the Lambda image) is
      unchanged, so the AWS artifact does not grow.
- [x] **5g. Accuracy harness** — `tests/test_accuracy.py`, marker `accuracy`, over the **five**
      fixtures in `tests/e2e_config.json`. Per fixture: cell accuracy, exact-grid match, puzzle
      validity, and whether `expected_empty_cells` were respected (the coloured-cell trap that
      motivated `IR-PROC-013`). Compares against a committed per-provider baseline, not an
      absolute threshold, so model non-determinism does not cause flakes.
      @spec IR-TEST-001, IR-TEST-002
- [x] **5h. Comparison runner** — `scripts/local/image-accuracy-compare.sh`, mirroring
      `coach-quality-repeat.sh`: N runs per provider, side-by-side summary.
      @spec IR-TEST-003
- [x] **5i. Keep it out of the default suites** — add `accuracy` to the marker exclusion in
      `local-alltests.sh` Suite 1, exactly as `e2e` already is. It needs live credentials and
      costs money per run, so it must not be in CI or the mandatory pre-push gate.
      @spec IR-TEST-003

## 4. Definition of Done

- [x] `pytest -m "not real_images and not e2e and not accuracy"` green with `IMAGE_AI_PROVIDER`
      unset — the existing suites unchanged in behaviour
- [x] Unit tests prove the factory selects each adapter, and that an unknown value raises
- [x] A Vertex SDK failure surfaces as `ProviderError` and is absorbed by the existing retry loop
- [x] Coverage stays at or above the `fail_under = 70` in `image_recognition/pyproject.toml`
- [x] The accuracy harness runs both providers over all five fixtures
- [x] **The cutover gate**: Vertex `gemini-2.5-flash` scores ≥ Bedrock Claude Haiku on mean cell
      accuracy *and* exact-grid count, across ≥3 runs
- [x] `IR-AI-001..006` and `IR-TEST-001..003` flipped to `[x]`
- [x] AWS behaviour byte-for-byte unchanged — `infra/aws` untouched, `BEDROCK_MODELS` still read

## 5. Outcome — Vertex passes, on a newer model

Measured 2026-09-14 over the five fixtures:

| Provider | Model | Mean cell accuracy | Exact grids |
| --- | --- | --- | --- |
| Bedrock | `claude-haiku-4.5` | 100% | 5/5 |
| **Vertex** | **`gemini-3.8-flash`** | **100%** | **5/5** |
| Vertex | `gemini-2.5-pro` | 98.8% | 3/5 |
| Vertex | `gemini-2.5-flash` | 92.6% | 0/5 |

`gemini-3.8-flash` matches Bedrock exactly and held at 100% across four runs. **The gate passes**,
so `IMAGE_AI_PROVIDER=vertex` is viable and `CP-GCP-085` can be retired at Phase 9.

### Two wrong turns on the way, both worth recording

**A configuration bug nearly produced a false negative.** The first Vertex run scored 56.8% with
two outright parse failures, which reads as a recognition problem. It was not: Gemini 2.5 thinks
by default and charges thinking tokens against `max_output_tokens`, so the 2048 copied from the
Bedrock path left too little for the answer and responses truncated mid-grid with
`FinishReason.MAX_TOKENS`. Claude spends all 2048 on output; Gemini spent ~900 before writing a
character. Disabling thinking — the prompt already asks for a visible `<scratchpad>`, so hidden
reasoning is redundant — moved it to 92.6%. Only that number was ever a fair comparison.

**The remaining gap was the model generation, not the prompt.** The plan's first remedy was
Gemini-specific prompt tuning, accepting an `IR-AI-004` exception. That would have been wasted
work: the identical prompt scores 100% on `gemini-3.8-flash`. Listing the published models first
cost one API call and removed the need for any prompt change. `IR-AI-004`'s byte-identical rule
stands unmodified.

**Gemini 3.x is served only from the `global` endpoint.** A regional request 404s with a message
blaming the model name or project access, which reads as a naming problem rather than a location
one. The adapter defaults to `global` for that reason.

### Consequences

- GCP image recognition can stop calling AWS Bedrock.
- The cross-cloud AWS access key, `CP-GCP-085`, and `scripts/infra/gcp/bedrock-cross-cloud.sh` can
  all be retired at Phase 9.
- "GCP is independent of AWS" becomes true once Phase 6 deploys with `IMAGE_AI_PROVIDER=vertex`.
- The new project keeps its "zero long-lived credentials" property — Secret Manager stays
  unenabled.

### Cost, measured

Token usage over the five fixtures, one run each:

| | Input | Output |
| --- | --- | --- |
| Bedrock `claude-haiku-4.5` | 7,941 | 4,451 |
| Vertex `gemini-3.8-flash` | 7,302 | 6,253 (2,531 answer + 3,722 thinking) |

| Configuration | Per image | Per 1,000 |
| --- | --- | --- |
| Bedrock, `eu-west-2` (+10% regional premium) | $0.00664 | $6.64 |
| **Vertex `gemini-3.8-flash`, promo to 2026-12-31** | **$0.00545** | **$5.45** |
| Vertex, standard from 2027-01-01 | $0.01091 | $10.91 |

Vertex is ~18% cheaper today and roughly 64% *more* expensive from January, when the
introductory rate doubles. **That reversal is a calendar event, not a decision** — worth a
diarised review rather than a surprise on a bill.

Older flash generations are not an option: `gemini-3.5-flash` missed the promotional pricing and
costs 2.3x `3.8-flash` while being three generations older. `3.6` and `3.7` are priced identically
to `3.8`, which outperforms both.

### Thinking tokens cannot be switched off

Roughly 60% of Vertex's output spend is reasoning tokens, and **no setting recovers it**:

- `MINIMAL` is rejected outright — *"Thinking level is unsupported"*
- `thinking_budget=0` is **silently ignored** on 3.x; it is the 2.x dialect
- `thinking_level=LOW` is the floor, saving ~5% with accuracy unchanged

The two dialects are mutually exclusive — each generation rejects the other's field with a 400 —
so the adapter picks by model prefix. The hypothesis that suppressing thinking would make Vertex
55% cheaper than Bedrock is **disproved**; the real figure is 18%.

### Still worth doing

Five fixtures at 100% leaves **no headroom to detect regression** in either provider. A larger
fixture set would make the baseline meaningful rather than saturated. Worth a backlog row.

## 5b. Original guidance (retained)

**The work still merges.** The switch defaults to `bedrock`, so nothing regresses. But say the
consequence plainly rather than discovering it at Phase 9: GCP keeps calling Bedrock, the
cross-cloud AWS key survives, `CP-GCP-085` cannot be struck through, and
`scripts/infra/gcp/bedrock-cross-cloud.sh` cannot be deleted. GCP would then be *almost*
independent of AWS, which is a materially weaker claim than the one this re-platform is making.

If that happens, the options in order: try `gemini-2.5-pro` (more capable, more expensive);
revisit the prompt for Gemini specifically, accepting that `IR-AI-004`'s byte-identical-prompt
rule would need an explicit exception; or accept Bedrock for image recognition and scope the
independence claim honestly.

## 6. Risks

| Risk | Mitigation |
| --- | --- |
| **`google-genai` moves fast** — the coach already hit a deprecation-forced migration mid-flight | Pin the version in `requirements-vertex.txt`. Verify `types.Part.from_bytes` and `system_instruction` against the pinned release rather than from memory. |
| **Gemini misreads grids** where Claude Haiku does not | That is what 5g measures and what the gate is for. Full `flash`, not `flash-lite`. |
| **Five fixtures is a small sample** | Enough to catch a gross regression, not enough to rank close results. Treat a narrow win as a tie and prefer the incumbent; ≥3 runs per provider to see variance. |
| **The refactor breaks the scoring loop**, which is the most valuable logic here | The loop is not modified — only its exception types and the call it makes. Existing tests cover it and must stay green without modification. |

## 7. Out of scope

- Any infrastructure change (`IMAGE_AI_PROVIDER=vertex` is set on Cloud Run in Phase 6)
- Migrating the **coach** — already on Vertex
- PIL preprocessing (`IR-PROC-001..005`, still deferred on the colour-cell problem)
- Removing Bedrock from the AWS Lambda path
