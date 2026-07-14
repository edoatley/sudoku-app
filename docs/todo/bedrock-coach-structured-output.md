# Investigate Bedrock structured output (JSON schema) for the coach response

**Summary:** Evaluate switching the coach's Bedrock call from `InvokeModel` (raw Anthropic
Messages API, freeform text the code then parses as JSON) to the `Converse` API's structured
output support, so the JSON-only contract is enforced by Bedrock itself instead of by prompt
wording plus a best-effort parser.

**Origin:** Raised in conversation during PR #132 (escalation-ladder context) follow-up —
`bedrock_client.converse(..., outputConfig={"textFormat": {"type": "json_schema", ...}})`
returned structured output matching a supplied JSON schema.

## Why this could help

The whole reason `parseResponse()`, `extractJsonObject()`, and `rawResponseText` (SC-BE-016,
SC-BE-021, SC-BE-022, SC-BE-023) exist is that the current approach has no way to *guarantee*
the model's reply is valid JSON — only to detect and gracefully degrade when it isn't. The
system prompt's entire "OUTPUT FORMAT — MANDATORY" section and "FINAL REMINDER" (added in
PR #130) are wording-based enforcement of a contract the API itself doesn't enforce. Even after
PR #132's escalation-ladder change, one real fallback in 110 calls was still exactly this: a
prose reply with no JSON at all (`ui/tests/coach-quality/reports/haiku-4-5-baseline/summary.txt`).
If Bedrock can enforce the schema server-side, that entire class of failure (Finding 1 from
`docs/todo/bedrock-coach-prompt-quality-findings.md`) could be eliminated rather than reduced.

## Key open design question — guidance vs. coordinate replies

A single rigid schema doesn't fit today's `{aiMessage, revealHint}` contract cleanly, for the
same reason PR #132 had to caveat `revealHint`'s meaning: `HintResponse.reveal()` is a single
cell + digit statement for only 3 of 11 hint strategies (Naked Single, Hidden Single, Full
House); the other 8 (pairs, triples, pointing pairs, X-Wing, Swordfish, Y-Wing) are elimination
facts with no single coordinate to report (see `BedrockCoachClient.java` few-shot Example E and
the OUTPUT FORMAT clarification added in PR #132). A schema that always requires a `coordinates`
field would either force the model to fabricate one on elimination techniques (reintroducing
hallucination risk RULE 3 exists to prevent) or need the field to be genuinely optional/nullable
— which then needs a way to distinguish *why* it's null: "this reply is guidance, no reveal
yet" vs. "this technique has no single coordinate to reveal, here's the elimination instead."

Concretely, the schema probably needs a discriminated shape along these lines (illustrative,
not a proposal to implement yet):
```
{
  "aiMessage": string,
  "replyType": "guidance" | "coordinateReveal" | "eliminationReveal",
  "revealedCell": {"row": int, "col": int, "digit": int} | null,   // only when coordinateReveal
  "revealedElimination": string | null                              // only when eliminationReveal
}
```
This is close to the "structured `RevealFact`" idea already flagged as a deferred follow-on in
PR #132's plan (`docs/specs/sudoku-coach-specs.md` SC-API-012's surrounding discussion) — that
idea and this one should be designed together, not separately, since both are about replacing a
free-text/boolean-self-report contract with a schema-checked one.

## What needs verifying before design work starts

None of this is confirmed against the current state of AWS Bedrock — verify first, don't assume:
1. **Model support** — does `Converse`'s structured output (`outputConfig.textFormat.json_schema`,
   or Bedrock's tool-use-based structured output pattern, whichever is the actual current
   mechanism) support the Claude Haiku 4.5 cross-region inference profile currently in use
   (`eu.anthropic.claude-haiku-4-5-20251001-v1:0`)? The example that prompted this doc used an
   older Sonnet model ID.
2. **Prompt caching compatibility** — `cache_control: {type: ephemeral}` on the system prompt
   is load-bearing (LLD: LangChain4j was rejected specifically because it didn't expose
   `cache_control`; the system prompt is deliberately sized to exceed Haiku's 2,048-token
   caching threshold). Confirm `Converse` supports `cache_control` equivalently before assuming
   this migration is cost-neutral.
3. **API migration cost** — `Converse` has a different request/response shape than `InvokeModel`
   (`BedrockRuntimeClient.converse()` vs `.invokeModel()`, different Java SDK types). This is a
   rewrite of `buildRequestJson()`/`parseResponse()`, not a parameter addition.
4. **Timeout/latency behaviour** — confirm schema-constrained generation doesn't materially
   change latency against the existing `BEDROCK_TIMEOUT_SECONDS = 6` budget.

## What to do

1. Verify the four points above against current AWS Bedrock documentation and, if needed, a
   small standalone spike (not integrated into `BedrockCoachClient` yet) calling `Converse`
   with a JSON schema against the real Haiku 4.5 model.
2. If viable, design the discriminated reply schema properly — likely paired with the deferred
   `RevealFact` structured-type idea from PR #132, since both replace the same free-text/boolean
   contract.
3. Only then touch `docs/specs/sudoku-coach-specs.md` (SC-BE-014, SC-API-012 would both need
   revising for a schema-enforced contract) and `BedrockCoachClient`'s request/parse code —
   this is LLD/EARS-level work per the project's linked-intent-dev workflow, not a quick patch.
4. Validate with the `ui/tests/coach-quality/` harness the same way PR #130/#132 did — a real
   before/after baseline, not a single run.

## Acceptance criteria

- [ ] Confirmed (or ruled out) whether Bedrock structured output supports Haiku 4.5 +
      `cache_control` together
- [ ] Discriminated schema designed and reviewed (guidance / coordinate reveal / elimination
      reveal), specifically covering all 11 hint strategies' `reveal()` shapes
- [ ] Real Bedrock baseline comparing fallback rate against the PR #132 baseline
      (`ui/tests/coach-quality/reports/haiku-4-5-baseline/summary.txt`, 0.9% overall)

## Related specs / docs

- `docs/todo/bedrock-coach-prompt-quality-findings.md` — Finding 1 (prose-with-no-JSON), the
  failure mode this could eliminate rather than reduce
- `docs/specs/sudoku-coach-specs.md` — SC-BE-014 (JSON-only output instruction), SC-BE-016/021/022
  (fallback/parse-tolerance machinery this could partly retire), SC-API-012 (`revealHint`
  semantics)
- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java` — current
  `InvokeModel`-based implementation
- `backend/src/main/java/com/sudoku/puzzle/web/HintResponse.java`,
  `backend/src/main/java/com/sudoku/puzzle/hint/*Strategy.java` — the 11 strategies whose
  `reveal()` text would need to map onto the schema's discriminated shape
