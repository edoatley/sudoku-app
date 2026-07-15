# Implementation Plan: Bedrock Coach Structured Output + Caching

**Status**: Approved — ready to implement
**Created**: 2026-07-14
**Origin**: `docs/todo/bedrock-coach-structured-output.md` (PR #132 follow-up)
**Arrow**: `sudoku-coach` (`docs/arrows/sudoku-coach.md`)

---

## 1. Context

The AI coach (`BedrockCoachClient`) calls Bedrock via `InvokeModel` with a raw Anthropic
Messages body and enforces its `{aiMessage, revealHint}` JSON contract **purely through prompt
wording** (`OUTPUT FORMAT — MANDATORY` + `FINAL REMINDER`) plus a best-effort parser
(`extractJsonObject`, `parseResponse`, `rawResponseText`). The residual failure this cannot
prevent is the model replying in prose with no JSON at all — exactly the 1-in-110 fallback in
the baseline (`ui/tests/coach-quality/reports/haiku-4-5-baseline/summary.txt`,
`deep-escalation-ladder`, `JsonParseException`).

**Goal 1 (reliability):** make the JSON contract **server-enforced by Bedrock structured
output** instead of prompt-enforced, eliminating that failure class rather than reducing it.

**Goal 2 (cost):** fix prompt caching. The baseline shows `cacheRead=0 cacheWrite=0` across all
110 calls — caching is not working today, a live regression against the project's
cost-protection standards.

---

## 2. Verified findings

Confirmed against current AWS Bedrock docs (structured-outputs GA, Feb 2026) and by static
inspection of the cached AWS SDK:

1. **Haiku 4.5 supports structured output** on both Converse (`outputConfig.textFormat`) and
   InvokeModel Anthropic-native (`output_config.format`). Schema enforcement does **not** require
   migrating to Converse — it can be added to the existing InvokeModel body with a small JSON
   change.
2. **Caching differs by API.** InvokeModel Anthropic body uses `cache_control:{ephemeral}` (valid
   on the passthrough, what the code does today). Converse uses `cachePoint` blocks
   (`{"type":"default","ttl":"5m"|"1h"}`); the `ephemeral` form is silently ignored on Converse.
3. **Cache minimum for Haiku 4.5 is 4,096 tokens**, not 2,048. The LLD (~L425) and the code
   comment (`BedrockCoachClient.java` ~L33) are stale. The system prompt is ~15.4K chars
   ≈ ~3,800–4,000 tokens — at or under threshold, the likely cause of zero cache hits.
4. **Schema constraints** (JSON Schema Draft 2020-12 subset): `additionalProperties:false`
   required on every object; `anyOf`+`const` for discriminated unions (no `oneOf`); no numeric
   `minimum`/`maximum`, no `minLength`/`maxLength`. The minimal schema below needs none of these.
5. **Grammar compile latency:** a new schema is compiled server-side (up to a few minutes) then
   cached 24h per account. Only the first-ever call for a schema risks the 6s timeout; mitigate
   with a post-deploy warm-up call.
6. **SDK support — RESOLVED (no pom change needed).** The typed Converse structured-output
   surface is present in the cached SDK: `OutputConfig.textFormat()` → `OutputFormat`
   (`type` + `structure`) → `OutputFormatStructure` → `JsonSchemaDefinition{schema, name,
   description}` (`schema` is a stringified JSON), plus `CachePointBlock` and `ConverseRequest`.
   Verified present in **both** cached versions (`bedrockruntime` 2.44.6 and 2.46.17), so
   whichever the `quarkus-amazon-services-bom` 3.20.0 resolves, no explicit dependency override
   is required.

---

## 3. Decisions

- **Schema**: minimal enforced contract `{aiMessage: string, revealHint: boolean}`. Reveal
  coordinates stay authoritative in `HintResponse`; the frontend derives coordinate-vs-elimination
  from `solvedCells`/`eliminatedCandidates`. The LLM never re-emits coordinates (avoids RULE 3
  fabrication risk). Also fully unblocks the deferred frontend gaps SC-UI-050/051.
- **Rollout**: config feature-flag `coach.bedrock.api-mode = invoke | converse`, same `/ai/coach`
  endpoint. Structured output enabled in **both** modes so the A/B isolates API + caching
  differences, not the reliability change.
- **Caching**: investigate and fix properly — measure real token count, migrate converse mode to
  `cachePoint`, ensure the prompt exceeds 4,096 tokens, evaluate 1h TTL.
- **Prompt**: trim the now-redundant `OUTPUT FORMAT` / `FINAL REMINDER` sections, then re-pad
  above 4,096 tokens with genuinely useful content (extra few-shot examples / pedagogical
  guidance) so trimming and caching don't conflict.

---

## 4. Schema (shared by both API modes)

```json
{
  "type": "object",
  "properties": {
    "aiMessage":  { "type": "string",  "description": "The coach's reply to the player." },
    "revealHint": { "type": "boolean", "description": "true only when the reply explicitly gives the answer the deterministic hint would reveal." }
  },
  "required": ["aiMessage", "revealHint"],
  "additionalProperties": false
}
```

- **invoke mode** (Anthropic body): `"output_config": { "format": { "type": "json_schema", "schema": <schema> } }`
- **converse mode** (typed SDK): `OutputConfig.textFormat(OutputFormat.type(JSON_SCHEMA)
  .structure(OutputFormatStructure.jsonSchema(JsonSchemaDefinition.schema(<stringified>)
  .name("coach_reply").description(...))))`

Define the schema once as a constant, reference from both request builders.

---

## 5. Phased work

### Phase 0 — Live spike (throwaway, needs `AWS_PROFILE=sandbox`)

SDK capability is already confirmed statically (finding 6); the spike only needs to confirm
runtime behaviour against real `eu.anthropic.claude-haiku-4-5-20251001-v1:0`:
1. Structured output returns schema-valid JSON for a representative coach prompt.
2. `cachePoint` on the system block returns `cacheReadInputTokens>0` on a second identical call
   within TTL; measure the system prompt's real token count vs 4,096.
3. Latency steady-state within the 6s budget; observe first-call cold-schema compile behaviour.

### Phase 1 — LID docs (LLD → EARS) before code

- `docs/llds/sudoku-coach.md`: correct the caching threshold (4,096); add a "Structured output"
  subsection (schema + per-mode request shapes); add the `api-mode` flag and `cachePoint`
  mechanism; keep the LangChain4j-rejection note.
- `docs/specs/sudoku-coach-specs.md`:
  - **Revise** SC-BE-014 (JSON schema-enforced, prompt no longer sole guarantor), SC-BE-011
    (cache_control ephemeral *or* cachePoint per mode), SC-BE-012 (threshold 4,096).
  - **Add** SC-BE-025 (structured output enforces the `{aiMessage, revealHint}` schema),
    SC-BE-026 (`api-mode` selects invoke|converse; both enforce the schema), SC-BE-027 (converse
    mode uses `cachePoint`; prompt exceeds the 4,096 cache minimum).
  - SC-BE-016/021/022/023 **retained** as the safety net for timeouts/SDK errors; note their
    JSON-drift trigger frequency should drop to ~zero.
- `docs/arrows/index.yaml` + `docs/arrows/sudoku-coach.md`: bump `spec_count`/`implemented`, list
  new IDs.

### Phase 2 — Code

`backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java` (primary):
- Add `@ConfigProperty coach.bedrock.api-mode` (default `invoke`).
- Extract the shared output schema constant.
- **invoke mode**: extend `buildRequestJson()` to add `output_config.format`; otherwise unchanged
  (keeps `cache_control:{ephemeral}`, `parseResponse` reads `content[0].text`).
- **converse mode**: new `buildConverseRequest()` (typed `ConverseRequest`: system blocks =
  prompt text + `CachePointBlock`; messages from history + user; `inferenceConfig.maxTokens=512`;
  `outputConfig.textFormat` schema) and `parseConverseResponse()` (reads
  `output().message().content()...text()`, and `usage()` for input/output/cacheRead/cacheWrite
  tokens). Reuse existing `extractJsonObject`/`fallback`/`ParsedResponse`/logging.
- Dispatch on the flag in `call()`. Keep the single-call, no-tool-loop invariant (SC-BE-010).
- Trim `SYSTEM_PROMPT`'s `OUTPUT FORMAT` + `FINAL REMINDER`, then re-pad above 4,096 tokens with
  additional few-shot examples (guided by spike token measurement).

`backend/src/main/resources/application.properties`: add
`coach.bedrock.api-mode=${COACH_BEDROCK_API_MODE:invoke}`.

### Phase 3 — Tests

- Unit (`backend/src/test/.../coach/bedrock/`): assert `buildRequestJson` emits
  `output_config.format`; assert the converse request builder produces schema + cachePoint; assert
  `parseConverseResponse` maps a sample Converse response body (including `usage` cache token
  fields) to `ParsedResponse`.
- Harness A/B (`ui/tests/coach-quality/`): run the existing 8 scenarios against `api-mode=invoke`
  and `api-mode=converse`; compare `summary.txt` `fallbackRate`, `cacheRead`/`cacheWrite`, latency
  vs the 0.9% baseline. `deep-escalation-ladder` is the direct regression witness. No new
  scenarios needed.

---

## 6. Critical files

- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java` — main change.
- `backend/src/main/resources/application.properties` — `api-mode` flag.
- `docs/llds/sudoku-coach.md`, `docs/specs/sudoku-coach-specs.md`, `docs/arrows/index.yaml`,
  `docs/arrows/sudoku-coach.md` — LID intent updates.
- Unchanged/reused: `SudokuCoachServiceImpl`, `CoachResource`, `CoachResponse`, `HintResponse`,
  `BoardFormatter`, `CoachRateLimiter`, the coach-quality harness. `backend/pom.xml` needs **no**
  change (SDK support confirmed).

---

## 7. Verification / success criteria

1. Live spike confirms schema-valid output + `cacheRead>0` on repeat call; real prompt token
   count measured vs 4,096.
2. `./mvnw test` green (both request builders + converse parser).
3. Harness A/B: converse-mode and invoke-mode+schema both show JSON-drift `fallbackRate` = 0
   across ≥110 runs (vs 0.9% baseline); converse mode shows `cacheRead>0` after warm-up.
4. Prompt caching demonstrably active in ≥1 mode (`cacheReadTokens>0` in `COACH_RESPONSE`).
5. `bash scripts/local/local-alltests.sh` passes before push (MANDATORY per CLAUDE.md).
6. LID docs updated; arrows index reflects new spec coverage.

---

## 8. Cost implications

Rates (Claude Haiku 4.5 on Bedrock, mirroring Anthropic per-token pricing): input **$1.00/MTok**,
output **$5.00/MTok**, cache read **$0.10/MTok** (0.1×), 5-min cache write **$1.25/MTok** (1.25×),
1-hour cache write **$2.00/MTok** (2×). Baseline (`haiku-4-5-baseline/summary.txt`): n=110,
input≈3,365 tok/call, output≈98 tok/call, `cacheRead=0 cacheWrite=0`.

**Per-call cost today (no caching):** ≈ 3,365 × $1/MTok + 98 × $5/MTok ≈ **$0.0039/call**. The
system prompt dominates the input (~2,800–3,400 tok); the dynamic context (board + hint + history
+ user message) is only a few hundred tokens on top.

**Structured output — cost-neutral, reliability-positive.** The JSON schema adds no request tokens;
its grammar is compiled once and cached 24h per account (§2.5). It does not change per-call token
cost. It eliminates the prose-with-no-JSON fallback, but that path today just returns the nudge (no
retry), so there is no retry cost to save — the win is reliability, not spend. Trimming the now-
redundant `OUTPUT FORMAT`/`FINAL REMINDER` prose saves ~300–500 input tok/call (~$0.0004) **only if
we don't re-pad for caching** (§3, "Prompt" decision).

**Prompt caching — a win for multi-turn, a loss for one-shot.** Once the system prompt is cacheable
(≥4,096 tok, `cachePoint`):
- *cache write* (first turn): system prefix at 1.25× ≈ 4,096 × $1.25/MTok ≈ **$0.0051** — i.e. a
  single-turn interaction costs *more* than today's $0.0039 (you pay the write premium, get no read).
- *cache read* (turns 2+ within TTL): system prefix at 0.1× ≈ 4,096 × $0.10/MTok ≈ **$0.0004** +
  ~$0.0005 dynamic + ~$0.0005 output ≈ **$0.0014/call** — ~64% cheaper than uncached.

Break-even is **2 turns within the 5-min TTL** (per Anthropic's caching economics). The coach's
back-and-forth design favours this, but one-shot asks (several baseline scenarios are single-turn)
are net-negative. **Net effect depends on the real turns-per-conversation distribution — measure it
in the harness before committing.** `1h` TTL (write 2×, break-even 3 turns) only pays off if users
reliably return within the hour; for sporadic single-user sessions, default to `5m`.

**Budget-accounting interaction (flag).** `tokensUsed` today = `inputTokens + outputTokens`, and
`inputTokens` is the *uncached remainder* only. Enabling caching therefore silently **shrinks** the
figure charged against the 100,000-tok monthly budget (SC-RL-002) — cached prefix tokens move into
the separately-tracked `cacheRead`/`cacheWrite` fields, which are not added to `tokensUsed`. Users
would effectively get more coach turns per month. Decide whether the monthly budget should stay
token-based (simpler; under-reflects the write premium) or move to a cost-weighted unit
(`input + output + cacheWrite×1.25 + cacheRead×0.1`) for accurate spend tracking. Recommend keeping
it token-based for now and noting the caveat in the LLD.

**Bottom line:** absolute spend is tiny (sub-cent/call; the 100k-tok/user/month cap bounds it
regardless). The cost work is worth doing to (a) close the zero-cache-hits bug and (b) get accurate
visibility, not because the savings are large. Let harness-measured turn distribution decide the TTL
and the prompt-length/caching trade before optimising further.

---

## 9. Open items / risks

- **Caching economics**: with a 5-min TTL and sporadic single-user sessions, cache hits may still
  be rare in production; 1h TTL trades higher write cost for hit rate. Capture real hit rates in
  the harness before over-investing (see §8).
- **Cold-schema latency**: one-time compile could exceed 6s on the first call after a schema
  change; mitigate with a post-deploy warm-up invocation.
