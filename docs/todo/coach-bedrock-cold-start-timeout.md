# Coach can time out on its first invocation after a fresh deploy

**Summary:** The AI coach's first-ever request against a freshly deployed Lambda can exceed the
8s Lambda timeout and return a 500 — the Bedrock SDK client is cold in a way the existing
SnapStart warm-up doesn't cover. This is a real user-facing reliability gap, not just a smoke
test quirk: any player who's the first to use the coach after an RC (or production) deploy could
hit it.

## Context

**Relevant files:**
- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java` — `BEDROCK_TIMEOUT_SECONDS = 6` (SDK-level `apiCallTimeout`), intended to leave 2s headroom inside the 8s Lambda timeout — didn't help here; the whole Lambda was killed at 8000ms before the SDK's own timeout ever fired
- `.github/workflows/smoke-tests.yml` — "Warm up Lambda (RC only)" step polls `GET /api/v1/health` until 200 — this primes the HTTP/JVM layer via SnapStart resume, but never touches the coach's Bedrock client
- `docs/llds/sudoku-coach.md` — "Technical Debt / Open Questions" section already flagged the underlying CRaC/GraalVM/SnapStart question as unresolved; this todo adds concrete evidence
- `docs/planning/ai-guide.md` §12 — original spike plan for cold-start strategy, never completed

**Current state:**
Confirmed via `scripts/github/coach-smoke-test.sh` against `rc-ai-coach-improvements-1`'s
first-ever deploy (PR #117, 2026-07-09): the first `POST /api/v1/ai/coach` call logged a
`COACH_REQUEST` line successfully, then the Lambda's own CloudWatch `REPORT` line showed
`Duration: 8000.00 ms ... Status: timeout` — no `COACH_RESPONSE` was ever logged, meaning the
Bedrock `InvokeModel` call itself never returned. A second call immediately after (same warm
execution environment) completed in ~1.8–2.0s. The smoke test now retries once to work around
this (`COACH_HTTP_RETRY_ATTEMPTS`), but that's a workaround for CI, not a fix for real players.

**Key constraints:**
- Must follow `linked-intent-dev`: this affects coach reliability (SC-BE-015 already documents
  the 6s SDK timeout behaviour) — likely needs an EARS spec update once a fix lands, and touches
  `docs/llds/sudoku-coach.md`'s existing "Technical Debt" entry either way.
- SnapStart's own priming request (fired automatically by Quarkus during the init phase) is the
  natural place to add coach-specific warm-up, if that's the chosen fix — but confirm what code
  path it currently exercises before assuming it's HTTP-only.
- Don't conflate with the general CRaC vs. GraalVM native vs. SnapStart spike (`docs/planning/ai-guide.md`
  §12) — that's about overall cold-start latency; this is specifically about the Bedrock SDK
  client never being touched by whatever warm-up already exists.

## What to do

1. Confirm exactly what Quarkus's SnapStart priming request exercises (check whether it's
   possible to extend it, or trigger a supplementary priming call that touches
   `BedrockRuntimeClient`, `DynamoDbEnhancedClient`, etc. — not just the HTTP routing layer).
2. Consider whether a lightweight "warm the Bedrock client" call during Lambda init (before
   SnapStart snapshot, or via a CRaC `Resource` hook) is feasible without adding real Bedrock
   cost per cold start.
3. Alternatively (or additionally): increase `BEDROCK_TIMEOUT_SECONDS` headroom or the Lambda
   timeout itself for the coach path specifically, if warming isn't practical — weigh against
   `docs/arrows/security-standards.md`'s Bill Protection guidance (Lambda timeouts sized to fail
   fast, not stall).
4. Update `docs/specs/sudoku-coach-specs.md` (likely extending SC-BE-015's fallback-on-timeout
   coverage, or adding a new spec for "first invocation after deploy must not exceed X seconds")
   once a fix direction is chosen — full linked-intent-dev workflow, since this is behavioural.
5. Re-run `scripts/github/coach-smoke-test.sh` against a fresh workspace deploy to confirm the
   first call no longer needs the retry.

## Acceptance criteria

- [ ] First-ever coach call after a fresh deploy reliably completes within the Lambda timeout
      (verified against a genuinely new workspace, not a warm one)
- [ ] `scripts/github/coach-smoke-test.sh`'s `COACH_HTTP_RETRY_ATTEMPTS` workaround can be
      reduced back to 1 (no retry needed) without the smoke test flaking
- [ ] `docs/llds/sudoku-coach.md` Technical Debt entry and `docs/specs/sudoku-coach-specs.md`
      updated to reflect the resolution

## Related specs / docs

- [`docs/llds/sudoku-coach.md`](../llds/sudoku-coach.md) — Technical Debt / Open Questions section
- [`docs/planning/ai-guide.md`](../planning/ai-guide.md) §12 — original cold-start spike plan
- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — SC-BE-015 (existing 6s timeout fallback behaviour)
