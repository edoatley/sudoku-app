# Add Vertex AI context caching to VertexCoachClient

**Summary:** `VertexCoachClient` sends the full ~4,000+ token tutor system prompt on every
call with no caching, unlike `BedrockCoachClient` which caches it (SC-BE-011/012/027) — Vertex
AI has an equivalent context-caching feature that isn't used yet.

**Branch context:** `feat/coach-quality-remote-vertex-compare` — found while running a real
Bedrock-vs-Vertex coach-quality comparison to validate the Vertex cutover (CP-GCP-090).

## Why deferred

Out of scope for the comparison run itself — this is a finding from that run, not something to
fix mid-validation. Recorded as its own item since it materially affects the cost side of the
SC-GCP-007 cutover decision.

## Update (2026-08-20): SDK migration is a prerequisite

Investigation found this isn't a contained caching change. `VertexCoachClient` was built on
`com.google.cloud.vertexai.generativeai.GenerativeModel`, deprecated by Google in June 2025 with
a stated removal date of June 24, 2026 (already past). That package also has no hook to attach a
`CachedContent` resource and no bundled client to manage cache resources at all — caching is only
reachable via Google's new unified `com.google.genai:google-genai` SDK, which has first-class
`cachedContent` support.

The prerequisite migration — `VertexCoachClient` moved to `com.google.genai`, functionally
identical, no caching yet — landed first (separate PR from this work item). The caching work
described below is the follow-on, now unblocked: `GenerateContentConfig.builder().cachedContent
(name)` and `client.caches` are both confirmed present in the new SDK (verified directly against
the resolved 1.66.0 jar).

## Context

**Relevant files:**
- `backend/src/main/java/com/sudoku/coach/vertex/VertexCoachClient.java` — no caching; the
  `CoachPromptBuilder`-produced system prompt is sent fresh every call.
- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java` — reference
  implementation of the caching pattern this should mirror conceptually (`cache_control` /
  `CachePointBlock` depending on `coach.bedrock.api-mode`).
- `docs/specs/sudoku-coach-specs.md` — `SC-BE-011/012/027` (Bedrock caching requirements);
  `SC-GCP-005` (Vertex token reporting — currently reports one `tokens` total, no cache
  read/write split to report even if caching were added, since Gemini's `usageMetadata` has its
  own cached-content-token field to plumb through).

**Current state:**
A real local comparison (`docker-compose.coach-quality-vertex.yml`, `gemini-2.5-flash-lite`, 55
turns) measured Vertex at ~4,923 raw tokens/turn vs Bedrock's ~4,777 raw tokens/turn — nearly
identical raw volume. But Bedrock's total is ~89% cache reads (heavily discounted), while
Vertex pays full price for every token on every call. Latency was materially better for Vertex
(mean 1143ms vs 2041ms), so this isn't a "Vertex is worse" finding — it's specifically a cost
gap from an unused feature, not an inherent Gemini limitation.

**Key constraints:**
- Vertex AI context caching (`CachedContent` resource, or inline cache TTL depending on API
  surface) has its own minimum-token-size and TTL mechanics, analogous to but not identical to
  Bedrock's — check current Vertex AI SDK docs for the caching API shape before implementing,
  since this is a fast-moving area of the SDK.
- The tutor system prompt is shared via `CoachPromptBuilder` — adding caching should not change
  prompt content, only how it's transmitted/cached per adapter.

## What to do

1. ~~Check the `google-cloud-vertexai` SDK version in use (`pom.xml`) for its context-caching
   API surface.~~ Done — see "Update (2026-08-20)" above; use `com.google.genai`'s `Caches`
   client and `GenerateContentConfig.cachedContent(name)` instead.
2. Add caching to `VertexCoachClient`'s system-prompt transmission, mirroring the intent of
   SC-BE-011/027 for the Vertex path.
3. Extend the `COACH_RESPONSE` log line to report cached vs fresh tokens for Vertex (parallel
   to Bedrock's `cacheReadTokens`/`cacheWriteTokens`), and update `aggregate.js` if the field
   names differ from what it currently expects.
4. Re-run the local comparison (`docker-compose.coach-quality-vertex.yml`) to confirm the total
   token volume drops toward Bedrock's cached-adjusted cost.

## Acceptance criteria

- [ ] `VertexCoachClient` uses Vertex AI context caching for the system prompt
- [ ] A repeat coach-quality comparison run shows materially lower total token cost per turn
      than the 270,775/55-turn baseline recorded here
- [ ] Cached vs fresh token counts are visible in the `COACH_RESPONSE` log line and the
      coach-quality aggregate summary

## Related specs / docs

- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — `SC-BE-011/012/027`
  (Bedrock caching, the pattern to mirror), `SC-GCP-005` (Vertex token reporting)
- [`docs/llds/sudoku-coach.md`](../llds/sudoku-coach.md) — AI Provider Port / Vertex AI section
