# BedrockCoachClient prompt/parsing quality findings

**Superseded (2026-07-14):** The wording-only fix below reduced both findings from ~20-40% to
~11%. A follow-up change (PR #132, `docs/specs/sudoku-coach-specs.md` SC-BE-003/SC-BE-024) went
further: `BedrockCoachClient` now sends all three of the hint engine's escalating explanation
levels (`nudge`/`focus`/`reveal`) plus a suggested turn-based escalation level, instead of just
`nudge` on every turn — giving the model solver-verified material (including an exact
cell+digit statement for placement techniques) instead of asking it to compose the answer from
scratch. A real 10-run baseline post-#132 measured both `naked-single-conversation` and
`explicit-answer-request` (this doc's two named scenarios) at 0% fallback (0/20, 0/10) — see
`ui/tests/coach-quality/reports/haiku-4-5-baseline/summary.txt`. This doc's own resolution and
acceptance criteria below are left as the historical record of the wording-only fix; they are
not the current state of the art for these two failure modes.

**Resolution (2026-07-13):** Measured a 10-run real-Bedrock baseline before editing anything,
per the concern that a single clean run (`*-2026-07-12T18-49-*`) might just be noise. Baseline
confirmed both findings as real and frequent enough to act on: Finding 1 (naked-single-conversation)
failed 2/10 runs (20%), Finding 2 (explicit-answer-request) failed 4/10 runs (40%). Applied two
surgical, additive `SYSTEM_PROMPT` edits — a "FINAL REMINDER" section reinforcing JSON-only
output at the very end of the prompt (Finding 1), and a strengthened `ALWAYS` clause in Rule 2
ruling out a follow-up question as an acceptable reply to an explicit ask (Finding 2) — then
re-ran 10 more times. Post-edit: Finding 1 dropped to 1/9 (11%, excluding one run confounded by
an unrelated ~25-minute Bedrock/log-correlation timeout anomaly — see report
`naked-single-conversation-2026-07-13T07-29-22-952Z.json`), ending in 6 consecutive passes;
Finding 2 dropped to 1/9 (11%), ending in 7 consecutive passes. Both clear the acceptance
criteria below. All 37 backend coach unit tests (`BedrockCoachClientTest` et al.) pass unchanged
— no parsing/fallback contract was touched. Both findings are non-deterministic model behaviour,
not eliminated outright — the wording edits reduced frequency, they didn't guarantee it can't
recur; the deterministic-nudge fallback (SC-BE-016/017) remains the backstop.

Four new scenarios were also added for previously-untested pedagogical rules: `off-topic-message`
(Rule 5), `wrong-guess-acknowledgment` (Rule 4), `deep-escalation-ladder` (Rule 2 turns 3-4), and
`technique-explanation-ask` (Example D). `deep-escalation-ladder` already caught a real
Finding-1-style fallback on a later conversation turn during the baseline run, confirming it adds
genuine coverage beyond the original 4 scenarios.

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

- [x] `naked-single-conversation` scenario passes (`coachFallback: false` on both `ask` turns)
      across at least 3 consecutive runs. (6 consecutive post-edit, runs 5-10 of 10.)
- [x] `explicit-answer-request` scenario passes (`coachLogContains: "revealHint":true`) across
      at least 3 consecutive runs. (7 consecutive post-edit, runs 4-10 of 10.)
- [x] No regression in `backend/src/test/java/com/sudoku/coach/` unit tests. (37/37 pass.)

## Related specs / docs

- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — SC-BE-014–017 (JSON
  output contract, fallback guarantees), SC-BE-021/022 (fallback flagging, JSON extraction).
- [`docs/specs/coach-quality-specs.md`](../specs/coach-quality-specs.md) — the diagnostic
  runner's own spec, CQ-RUN-002 in particular (how the runner surfaces a missing/errored coach
  log pair, which is how these findings were made visible).
