# BedrockCoachClient prompt/parsing quality findings

**Summary:** The new coach-quality diagnostic runner (`ui/tests/coach-quality/`) surfaced two
real model-behaviour gaps in the AI coach's Bedrock integration — not bugs in the tool itself.

**Branch context:** `coach-quality-tool` — PR #127 added the diagnostic runner and fixed 8
bugs in it found by review; these two findings came out of actually running it against real
Bedrock (`AWS_PROFILE=sandbox bash scripts/local/coach-quality-test.sh`) and are out of scope
for that PR.

## Why deferred

PR #127 is about the diagnostic tool's own correctness, not the coach's prompt quality. The
tool worked correctly and did its job: it caught two genuine `BedrockCoachClient` issues with
full evidence (structured logs, request/response pairs). Fixing prompt/model behaviour is a
different, open-ended kind of work (prompt tuning, possibly evals across many phrasings) that
deserves its own PR rather than blocking this one.

## Context

**Relevant files:**
- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java` — builds the system
  prompt (`SYSTEM_PROMPT`, starting line 35) and parses the model's reply (`parseResponse`,
  line 309; `extractJsonObject`, line 337).
- `ui/tests/coach-quality/scenarios/naked-single-conversation.js` — reproduces finding 1
  reliably on its second turn.
- `ui/tests/coach-quality/scenarios/explicit-answer-request.js` — reproduces finding 2.
- `ui/tests/coach-quality/reports/` (gitignored, local only) — past run evidence:
  `naked-single-conversation-2026-07-12T11-10-06-591Z.json`,
  `explicit-answer-request-2026-07-12T11-10-13-375Z.json`.

**Current state:**
`SYSTEM_PROMPT` mandates a single-line JSON-only response (`{"aiMessage": ..., "revealHint":
...}`, lines 100–114) and, in Rule 2 (line 81), says "If a player explicitly asks for the
answer, give it clearly and set revealHint to true." `extractJsonObject` (line 337) already
tolerates markdown-fenced or prose-wrapped JSON by slicing from the first `{` to the last `}`
(added for `@spec SC-BE-022`), but has no fallback when the reply contains **no** JSON at all.

**Finding 1 — prose reply with zero JSON structure:** On a scenario's second coach turn ("is
that right?", 2 messages of history), Bedrock replied with plain prose starting with the word
"I" — no braces anywhere. `extractJsonObject` can't extract what isn't there, so
`objectMapper.readTree` throws (`Unrecognized token 'I'`), triggering the deterministic-nudge
fallback. This has reproduced repeatedly in this exact spot during development per the
scenario file's own comment.

**Finding 2 — revealHint not set despite an explicit ask:** For the message "Just tell me the
answer, I give up.", the model gave a real (well-formed JSON, non-fallback) reply, but chose to
ask a Socratic follow-up question instead of stating the digit/cell, so `revealHint` stayed
`false`. Per the OUTPUT FORMAT section's own rule (line 110), `revealHint` is only `true` when
`aiMessage` **explicitly names both the exact cell and the exact digit** — so the model's
`revealHint:false` is internally consistent with what it actually said, but it isn't following
Rule 2's "give it clearly ... do not make them ask twice" instruction. This is the model not
adhering strongly enough to Rule 2, not a JSON-parsing bug.

**Key constraints:**
- `@spec SC-BE-014` through `SC-BE-017` (see `docs/specs/sudoku-coach-specs.md`) govern the
  JSON-only output contract and fallback behaviour — any fix must keep the "never 5xx, always
  degrade to nudge text" guarantee (`SC-BE-017`).
- Real Bedrock calls cost tokens and are non-deterministic — verify any prompt change by
  running `AWS_PROFILE=sandbox bash scripts/local/coach-quality-test.sh` multiple times, not
  once.

## What to do

1. Reproduce both findings by running `AWS_PROFILE=sandbox bash scripts/local/coach-quality-test.sh`
   a few times and confirming the `naked-single-conversation` / `explicit-answer-request`
   reports still show the same failure modes.
2. For finding 1 (prose replies): strengthen `SYSTEM_PROMPT`'s formatting enforcement for later
   turns in a conversation (the failure was turn 2, with history present) — e.g. reinforce the
   JSON-only instruction closer to the end of the prompt, or add a stop sequence / prefill
   technique so Bedrock can't drift into prose. Consider also whether `extractJsonObject`
   should have a documented, intentional last-resort behaviour when no JSON is found at all
   (today it correctly falls back — decide if that's sufficient or if a retry is warranted).
3. For finding 2 (revealHint not set): reinforce Rule 2's "give it clearly" instruction — the
   model needs to actually state the digit/cell when asked directly, not just ask a follow-up
   question. Consider adding another few-shot example showing a *stronger* "give up" phrasing,
   or making the ALWAYS clause more prominent/repeated.
4. Add or update `docs/specs/sudoku-coach-specs.md` EARS entries if the fix changes any
   observable contract (e.g. a new retry-on-empty-JSON behaviour).
5. Re-run the coach-quality suite several times post-fix to confirm both scenarios pass
   consistently (accepting some residual non-determinism is expected — the model is
   probabilistic).

## Acceptance criteria

- [ ] `naked-single-conversation` scenario passes (`coachFallback: false` on both `ask` turns)
      across at least 3 consecutive runs.
- [ ] `explicit-answer-request` scenario passes (`coachLogContains: "revealHint":true`) across
      at least 3 consecutive runs.
- [ ] No regression in `backend/src/test/java/com/sudoku/coach/` unit tests.

## Related specs / docs

- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — SC-BE-014–017 (JSON
  output contract, fallback guarantees), SC-BE-021/022 (fallback flagging, JSON extraction).
- [`docs/specs/coach-quality-specs.md`](../specs/coach-quality-specs.md) — the diagnostic
  runner's own spec, CQ-RUN-002 in particular (how the runner surfaces a missing/errored coach
  log pair, which is how these findings were made visible).
