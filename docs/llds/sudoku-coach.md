# Sudoku Coach (AI Tutoring System)

**Created**: 2026-07-04
**Status**: Complete
**HLD**: `docs/planning/ai-guide.md`
**Specs**: `docs/specs/sudoku-coach-specs.md`

---

## Context and Current State

The Sudoku Coach is a conversational AI tutoring system for novice players. It teaches *why*
a logical move works, rather than just disclosing the answer. It is a new component that sits
alongside the existing deterministic hint engine — both share the same underlying strategy
chain, but the coach adds a Bedrock-powered coaching layer on top.

The coach is desktop-only. The UX requires the board and chat to be simultaneously visible,
which is not achievable on mobile screen sizes.

The coach required extending the player profile to track cost guardrails. Changes to
existing files: `PlayerItem`, `PlayerProfile`, `PlayerUpdateRequest`, and `PlayerServiceImpl`
gained three new fields (`aiCoachEnabled`, `coachTokensUsedThisMonth`, `coachTokenMonth`).
All other additions — `CoachResource`, `SudokuCoachService`, `SudokuCoachServiceImpl`,
`BedrockCoachClient`, `CoachRateLimiter`, `BoardFormatter` — are new files.

---

## Backend Architecture

### Component Map

```
CoachResource                              ← REST adapter (input port)
        │  POST /ai/coach
        ▼
SudokuCoachService                         ← application port (interface)
        │
        ▼
SudokuCoachServiceImpl                     ← orchestrator (no Bedrock dependency)
        │                │
        ▼                ▼
SudokuService         BoardFormatter       ← reuse existing hint engine
(getHint())           (new utility)
        │
        ▼
BedrockCoachClient                         ← Bedrock adapter (output port)
        │
        ▼
BedrockRuntimeClient                       ← Claude Haiku via Amazon Bedrock
```

`CoachResource` also injects `PlayerService`, `PlayerRepository`, and `CoachRateLimiter` to
enforce per-user guardrails (toggle, monthly token budget, per-minute rate limit) before
delegating to `SudokuCoachService`.

`SudokuCoachServiceImpl` orchestrates the deterministic pre-analysis. `BedrockCoachClient` is
the only class that knows about Bedrock. This separation means:
- The deterministic backbone can be built and tested without any AI dependency.
- The Bedrock client can be swapped or mocked independently in tests.

### Interface

```java
public interface SudokuCoachService {

    CoachResult coach(CoachRequest request);

    sealed interface CoachResult permits CoachResult.Response, CoachResult.PuzzleSolved {
        record Response(CoachResponse coachResponse, long tokensUsed) implements CoachResult {}
        record PuzzleSolved() implements CoachResult {}
    }
}
```

`tokensUsed` in `Response` is the sum of `inputTokens + outputTokens` from the Bedrock usage
block. It is zero when the fallback path is taken. `CoachResource` increments the player's
monthly token counter only when `tokensUsed > 0`.

### Orchestration Flow (`SudokuCoachServiceImpl.coach()`)

```
1. hint = sudokuService.getHint(BoardRequest.of(request.board()))
   // Uses existing hint engine — same chain as /puzzles/hint

2. if hint is PuzzleSolved:
       return CoachResult.PuzzleSolved()         // CoachResource maps to 204

   if hint is NoStrategyApplied:
       return CoachResult.Response(NO_MOVES_MESSAGE, 0L)  // no Bedrock call

3. board = Board.fromGrid(request.board())
   board.calculateAllCandidates()
   // Computed here, only on the Found path — PuzzleSolved/NoStrategyApplied never reach
   // BedrockCoachClient, so computing candidates for those outcomes would be wasted work

4. context = BoardFormatter.format(board, hint)
   // Human-readable grid + technique name + relevant cells

5. history = trim(request.history(), MAX_HISTORY_MESSAGES)
   // Backend enforces cap; frontend may send more

6. result = bedrockCoachClient.call(userMessage, hint, history, board)
   // Single Bedrock call; falls back to hint.nudge() on failure (tokensUsed = 0 on fallback)

7. return CoachResult.Response(CoachResponse(result.reply, hint, result.revealHint, 0), result.tokensUsed)
   // tokensUsedThisMonth is a placeholder here — CoachResource rebuilds the record with the
   // real cumulative total before returning to the client (see CoachResponse below)
```

### `BoardFormatter` (new utility)

Converts `Board` to a human-readable string for the LLM prompt. Produces:

```
Row 1: 5 3 _ | _ 7 _ | _ _ _
Row 2: 6 _ _ | 1 9 5 | _ _ _
Row 3: _ 9 8 | _ _ _ | _ 6 _
       ------+-------+------
Row 4: 8 _ _ | _ 6 _ | _ _ 3
...
```

This format is significantly clearer to the LLM than `[[5,3,0,...]]`. It also converts
1-indexed cell references (as used in hint text) to match the board display.

### `BedrockCoachClient`

Responsible for:
1. Building the Bedrock/LangChain4j prompt with the tutor system message (cache-controlled)
2. Injecting the context block (board + technique)
3. Appending conversation history (trimmed)
4. Making a single Bedrock call, in either of two API modes (see "API modes" below)
5. Parsing the structured JSON response `{ "aiMessage": "...", "revealHint": true|false,
   "responseType": "..." }`, server-enforced by Bedrock structured output rather than prompt
   wording alone
6. Falling back to `hint.nudge()` + `revealHint=false` on timeout or parse failure
7. Logging a structured `COACH_REQUEST`/`COACH_RESPONSE` JSON line per turn, correlated by `cid`

**API modes:**
`coach.bedrock.api-mode` (`invoke` default, or `converse`) selects which Bedrock Runtime action
`BedrockCoachClient` calls. Both modes enforce the same output schema (see "Structured output"
below), so the flag isolates API-surface and caching-mechanism differences from the reliability
change:

| Mode | Bedrock action | Request shape | Cache mechanism |
|---|---|---|---|
| `invoke` (default) | `InvokeModel` | Raw Anthropic Messages body | `cache_control: {type: ephemeral}` on the system block |
| `converse` | `Converse` | Typed `ConverseRequest` | `CachePointBlock` (`{"type":"default"}`) appended to the system blocks |

**Structured output:**
Both modes constrain the model's reply to the shared schema `{aiMessage: string, revealHint:
boolean, responseType: enum}` (`additionalProperties: false`, all three fields `required`) —
enforced server-side by Bedrock, not prompt wording alone. This is defined once as a constant
and referenced by both request builders:
- `invoke` mode: `"output_config": { "format": { "type": "json_schema", "schema": <schema> } }`
  added to the Anthropic-native request body.
- `converse` mode: `OutputConfig.textFormat(OutputFormat.type(JSON_SCHEMA)
  .structure(OutputFormatStructure.jsonSchema(JsonSchemaDefinition.schema(<stringified schema>)
  .name("coach_reply").description(...))))`.

`responseType` is an enum of `nudge`, `focus-hint`, `reveal-answer`, `gentle-redirect`,
`off-topic-redirect`, `celebrate-progress`, `clarify-technique` — one per pedagogical scenario
already covered by the system prompt's rules and few-shot examples. It exists purely for
logging and automated testing (SC-BE-028): it is never included in the `CoachResponse` returned
to the frontend, only in the `COACH_RESPONSE` log line (see "Content logging" below). Live-spike
confirmed the `enum` JSON Schema keyword is supported by Bedrock's structured-output subset on
both API modes (not just the `anyOf`+`const` pattern used elsewhere for discriminated unions).
This directly replaces a flaky coach-quality harness assertion that substring-matched
non-deterministic reply prose (e.g. checking for the literal word "double-check") — the harness
now asserts on this schema-enforced category instead (`coachResponseType`, see CQ-AST-001).

Reveal coordinates stay authoritative in `HintResponse` — the LLM never re-emits them, only the
boolean `revealHint`; the frontend derives coordinate-vs-elimination disclosure from
`solvedCells`/`eliminatedCandidates`. This avoids RULE 3 fabrication risk (the LLM restating a
coordinate incorrectly) and keeps the schema minimal.

A new schema is compiled server-side on first use (observed: no material latency penalty on a
cold schema in this account — see Technical Debt) and cached 24h per account thereafter.

**Prompt caching:**
Verified against real Bedrock traffic (`eu.anthropic.claude-haiku-4-5-20251001-v1:0`,
`eu-west-2`): the cache minimum is **4,096 tokens**, not the 1,024/2,048 figures earlier
documentation assumed — a system prompt of ~3,187 tokens produced `cache_creation_input_tokens:
0` on both `InvokeModel` and `Converse`; a padded prompt of ~4,321 tokens produced a real cache
write, and a repeat call within the TTL produced a real cache read of the same size. Bedrock
caches the KV state for a 5-minute TTL (default) — `converse` mode's `CachePointBlock` supports
`"ttl":"1h"` as an alternative, at a higher write-cost multiplier; `invoke` mode's `cache_control`
form only supports the 5-minute TTL. Reducing system prompt token cost by ~90% on cache hits
only occurs once the prompt clears the threshold — include inline few-shot coaching examples to
reach it if needed (see "System Prompt" below).

**Raw Bedrock client:**
`BedrockRuntimeClient` is used directly (not LangChain4j). LangChain4j did not expose
`cache_control` blocks at the message level, so the raw SDK was chosen.

**Content logging:**
Each call to `BedrockCoachClient.call()` emits a `COACH_REQUEST`/`COACH_RESPONSE` pair to the
standard Quarkus/Lambda logger, sharing a `cid` (UUID, generated per call) so the two can be
joined, and `pid` (the game's `gameId`) so a coach turn can be joined with the player's
puzzle-play events for the same game. **Full field catalogue, the `pid`/`cid` correlation
model, and storage policy are documented centrally in `docs/llds/observability.md`** — this
section covers only this component's part: `board` and `candidatesGrid` are derived from the
single `Board` computed once in `SudokuCoachServiceImpl.coach()` (see Orchestration Flow step
3) and passed through, not recomputed here; `gameId` reaches the client via the `CoachRequest`
DTO and is threaded through `SudokuCoachServiceImpl.coach()` into `BedrockCoachClient.call()`
— if a client omits it (e.g. the coach is invoked outside a saved game), `pid` is logged as
null rather than failing the request. Lines are built via `objectMapper.writeValueAsString(...)`
(the already-injected `ObjectMapper`), never string templating, so freeform fields
(`userMessage`, `aiMessage`) escape correctly. `COACH_RESPONSE` also includes `responseType`
(SC-BE-029) when a genuine parse produced one; the key is omitted entirely on the fallback path,
since fallback never calls Bedrock's structured output and so has no model-chosen category to
report — matching the existing omit-when-absent convention already used for `rawResponseText`.

**Hint text coordinate conversion (`HintTextFormatter`):**
The hint engine is 0-indexed internally by design — `docs/specs/hint-engine-specs.md`'s
`HE-UI-005` states conversion to 1-indexed/named form happens "only at the display layer" and
must not mutate `HintResponse` itself. The frontend's hint dialog does this via
`ui/src/utils/hintDisplay.js`'s `formatHintText()`. The Bedrock prompt context is a second
display layer the frontend-only fix didn't cover: `BedrockCoachClient.buildContextBlock()` was
injecting `hint.nudge()`/`focus()`/`reveal()` raw, so the LLM received (and could repeat) 0-indexed
coordinates the player has no way to map onto the 1-indexed board — reported as: coach said "Row
7, Column 3 must be 1", but that cell is Row 8, Column 4 on the 1-indexed board (the highlighted
cell was correct; only the coach's stated coordinates were off, since highlighting uses the
underlying 0-indexed `Coordinate` data directly, unaffected by this bug).
`HintTextFormatter` (`backend/.../coach/bedrock/HintTextFormatter.java`) ports
`formatHintText()`'s regex-based conversion to Java — cell coordinates, block references,
single- and multi-unit row/column references — and `buildContextBlock()` applies it to
`nudge`/`focus`/`reveal` before they reach the prompt. Kept as a Java port rather than shared
logic (no practical way to share regex-based text transforms across the Java/JS boundary here);
`HE-UI-001..004`'s conversion rules are the single source of truth both ports must stay in sync
with (SC-BE-030 cross-references them rather than restating the rules).

Spec: `docs/specs/sudoku-coach-specs.md` — `SC-BE-005..030`.

### Constants

These live in their respective classes (no shared `CoachConstants` class):

```java
// SudokuCoachServiceImpl
static final int MAX_HISTORY_MESSAGES = 6;  // ~600 tokens

// BedrockCoachClient
static final int BEDROCK_TIMEOUT_SECONDS = 6; // leaves 2s headroom vs. 8s Lambda limit
static final int MAX_TOKENS = 512;
```

### Rate Limiting and Cost Protection (`CoachResource`)

Three guardrails are enforced in `CoachResource` before the request reaches
`SudokuCoachService`. All rely on the player profile loaded via `PlayerService`.

| Guardrail | Mechanism | Response on breach |
|---|---|---|
| AI coach toggle | `player.aiCoachEnabled() == Boolean.FALSE` | 403 |
| Monthly token budget | `usedThisMonth >= COACH_MONTHLY_TOKEN_LIMIT` | 429 (body: tokensUsed, monthlyLimit, resetsAt) |
| Per-minute rate limit | `CoachRateLimiter.tryConsume(userId)` via DynamoDB atomic UpdateItem | 429 + `Retry-After` header |

`CoachRateLimiter` uses a DynamoDB table (`SudokuCoachRateLimits{suffix}`, partition key
`userId`, sort key `window`) where `window` is the UTC minute string (`"yyyy-MM-dd'T'HH:mm"`).
A conditional `ADD callCount :one` increments the count; a `ConditionalCheckFailedException`
on `callCount < :limit` signals rate limit exceeded. The item TTL is 2 minutes from window
start. The limiter fails open on any non-conditional exception so infrastructure issues cannot
lock users out.

**Token increment timing and Lambda safety**

The token increment (`incrementCoachTokens`) is called synchronously *after* the Bedrock
call returns and *before* the HTTP response is sent. This is the correct pattern for Lambda:
after a handler returns its response, the execution environment is frozen immediately, so any
fire-and-forget async work may never complete. The synchronous path adds ~10–20 ms
(one DynamoDB write), negligible against the 2–6 s Bedrock call already in the path.

**Monthly budget is a soft limit (intentional)**

The budget check reads `coachTokensUsedThisMonth` from the profile snapshot taken at the
*start* of the request, before Bedrock is invoked. Under concurrent load near the limit, two
simultaneous requests can both pass the check using the same stale count, both call Bedrock,
and both increment — briefly exceeding the limit. The overage is at most one extra request's
token cost (~300 tokens, < $0.001 for Claude Haiku). Pre-reservation is not feasible because
token cost is unknown before invoking Bedrock. This soft-limit behaviour is intentional and
documented in code.

**Month rollover**

Monthly token tracking uses an atomic `UpdateItem` on the `SudokuPlayers` table:
- If `coachTokenMonth == currentMonth`, adds `tokensUsed` to `coachTokensUsedThisMonth` via
  `if_not_exists(..., 0) + :tokens` (handles first-ever coach call on a new profile)
- If month has rolled over (`ConditionalCheckFailedException`), resets the counter to
  `tokensUsed` (the current request's tokens only) with an unconditional write

Under concurrent load at the exact moment a month boundary is crossed, two requests can both
fail the conditional check and both execute the fallback write; the last writer wins and the
other's tokens are dropped from the counter. Impact: at most ~300 tokens under-counted at
the start of a new month. This is documented in code and accepted given the negligible cost.

Config properties:
```properties
coach.monthly-token-limit=${COACH_MONTHLY_TOKEN_LIMIT:100000}
coach.rate-limit.table-name=${COACH_RATE_LIMIT_TABLE_NAME:SudokuCoachRateLimits}
coach.rate-limit.per-minute=${COACH_RATE_LIMIT_PER_MINUTE:5}
%dev.coach.rate-limit.per-minute=1000
coach.bedrock.api-mode=${COACH_BEDROCK_API_MODE:invoke}
```

---

## Data Structures

### `CoachRequest`

```java
public record CoachRequest(
    Grid board,
    List<ChatMessage> history,
    String userMessage
) {}
```

| Field | Type | Notes |
|-------|------|-------|
| `board` | `Grid` | Existing wire type — not `int[][]` |
| `history` | `List<ChatMessage>` | Last ≤6 turns; trimmed server-side if over |
| `userMessage` | `String` | Non-blank; 1–500 chars |

### `ChatMessage`

```java
public record ChatMessage(
    String role,    // "user" or "assistant"
    String content
) {}
```

### `CoachResponse`

```java
public record CoachResponse(
    String aiMessage,
    HintResponse hint,
    boolean revealHint,
    long tokensUsedThisMonth
) {}
```

| Field | Type | Notes |
|-------|------|-------|
| `aiMessage` | `String` | Coaching prose from LLM (or fallback nudge text) |
| `hint` | `HintResponse` | Full deterministic hint — unchanged existing DTO |
| `revealHint` | `boolean` | Frontend gates whether `hint.solvedCells` are written into the current grid and `hint.eliminatedCandidates` removed from the candidate grid (SC-UI-050/051) — `hint.highlightCells` always take effect regardless |
| `tokensUsedThisMonth` | `long` | Player's cumulative monthly token count *after* this call. The domain layer (`SudokuCoachServiceImpl`) always passes 0; `CoachResource` rebuilds the record with the real total once it has computed the post-increment value, so the frontend can update its counter from the response instead of refetching the player profile (→ SC-RL-010). |

`HintResponse` is returned in full. The frontend decides which fields to show based on
`revealHint` and its own conversation state. The backend never partially populates it.

---

## System Prompt (Tutor Persona)

Stored as a constant in `BedrockCoachClient` (or an injected `@ConfigProperty` string).
Must be ≥4,096 tokens to qualify for prompt caching (see "Prompt caching" above). The required
elements:

1. **Persona**: Patient Sudoku tutor. Encouraging, never condescending.
2. **Constraint**: Do not invent moves. The board state and applicable technique are provided
   as context — refer only to cells and digits explicitly present in that context.
3. **Pedagogy rule**: Ask questions before giving answers. Guide the player to the insight.
   After 2–3 turns on the same technique without progress, disclose more.
4. **Output schema — advisory, not sole enforcement**: the prompt still describes the JSON
   shape and `revealHint`/`responseType` semantics for the model's benefit, but the shape itself
   is now server-enforced by Bedrock structured output (see "Structured output" above); the
   prompt no longer needs to be the sole guarantor of well-formed JSON, so the former `OUTPUT
   FORMAT — MANDATORY` / `FINAL REMINDER` sections are trimmed to a brief mention. `responseType`
   still needs prompt guidance even though its *shape* is schema-enforced (a fixed enum) — only
   the prompt teaches the model *which* category fits a given reply, since that's a semantic
   judgment the schema itself can't constrain.
5. **Few-shot examples** (to reach ≥4,096 token threshold and improve output quality): the
   trimmed length is re-padded with additional genuinely useful few-shot coaching examples /
   pedagogical guidance rather than repeated format warnings, so caching and prompt-trimming
   don't conflict.

---

## Input Validation

| Field | Rule | HTTP response on failure |
|-------|------|--------------------------|
| `board` | Not null; exactly 9 rows; each row exactly 9 integers 0–9 | 400 |
| `history` | Not null; if over 6 messages, trim (not reject) | — |
| `userMessage` | Not null, not blank, ≤500 chars | 400 |

History trimming (not rejection) is intentional: the frontend caps at 6 but should not be
treated as a hard contract violation if it occasionally sends more.

---

## HTTP Contract

```
POST /ai/coach
Authorization: Bearer {cognito-jwt}
Content-Type: application/json

Request:  CoachRequest
Response: 200 CoachResponse   — coaching response (AI or fallback)
          204                 — puzzle already solved, no technique applicable
          400                 — malformed board or blank userMessage
          401                 — missing or invalid JWT (enforced by API Gateway)
          403                 — player's aiCoachEnabled toggle is false
          429                 — monthly token budget exceeded (body: tokensUsed, monthlyLimit, resetsAt)
          429 + Retry-After   — per-minute rate limit exceeded
```

A `204` is returned only when `getHint()` returns `PuzzleSolved`. The endpoint never returns
a 5xx for a Bedrock failure — it degrades to the fallback response and returns 200.

Per-user guardrail order in `CoachResource.coach()`:
1. Toggle check (aiCoachEnabled == false → 403)
2. Monthly token budget check (usedThisMonth >= monthlyTokenLimit → 429)
3. Per-minute rate limit check (rateLimiter.tryConsume() → 429 + Retry-After)
4. Delegate to SudokuCoachService
5. Increment token counter on success

---

## Frontend Architecture

### Desktop-Only Guard

`CoachWidget.jsx` renders `null` when `useMediaQuery(theme.breakpoints.down('md'))` is true.
No coach button, no panel, no hook activity on mobile.

### Component Tree

```
App.jsx
└── CoachWidget.jsx              fixed position, bottom-right, desktop only
    ├── [FAB / open button]      opens the panel
    └── CoachPanel.jsx           the chat window (visible when open)
        ├── CoachMessage.jsx     × N  (AI left, user right)
        ├── [typing indicator]   shown while loading
        ├── [quick reply chips]  "I'm stuck", "Tell me more", "Why does that work?"
        └── [input row]          text field + send button
```

### `useCoachSession.js`

State owned by this hook:

| State | Type | Notes |
|-------|------|-------|
| `history` | `ChatMessage[]` | Grows per turn; capped to 6 before API call |
| `isLoading` | `boolean` | True while Bedrock call in flight |
| `highlightCells` | `Coordinate[]` | From last `CoachResponse.hint.highlightCells` |
| `isOpen` | `boolean` | Widget open/close state |

Key behaviours:
- On `open` (first time for current game): fire welcome message automatically
- On `sendMessage(text)`: append user turn, call API, append AI turn, update `highlightCells`
- On `newGame` event: clear history, clear highlights, close panel
- `highlightCells` from the coach takes precedence over the existing hint highlight while
  the coach panel is open; restores previous hint highlight on close

### Board-Chat Linkage

`CoachWidget` receives `setHighlightCells`, `setCurrentGrid`, and `setCandidateGrid` as props
from `App.jsx` (the same setters `useHintSystem` already owns/uses). When a coach response
arrives, `useCoachSession` calls `setHighlightCells(response.hint.highlightCells)` — this
always takes effect and updates the board immediately, the player sees the relevant cells
highlighted while the AI message is visible.

**Reveal writes to the board (SC-UI-050/051):** when `response.revealHint` is true,
`useCoachSession` additionally writes `response.hint.solvedCells` into the current grid
(clearing those cells' candidates) and removes `response.hint.eliminatedCandidates` from the
candidate grid — mirroring `useHintSystem.js`'s `advanceHint()` reveal-stage logic exactly, so
a coach reveal and a Hint-button reveal leave the board in the same state. When `revealHint` is
false, neither of these happens — only the highlight takes effect, and the player still places
the digit themselves. Before this, the coach could state the answer in its chat message
("Row 8, Column 4 must be 1") while the board only highlighted the cell, leaving the player to
type a digit the coach had already told them — reported as a UX inconsistency, since the
Hint button's own reveal stage does auto-fill.

This mirrors `useHintSystem`'s writes exactly (direct grid/candidate mutation, no `NUMBER`
event recorded, no undo-history entry, no localStorage persistence, no win-detection check) —
intentional parity with the existing Hint-button reveal path, not a new bookkeeping gap
introduced here.

When the coach panel closes, `setHighlightCells([])` is called to clear the coach's
highlights. If a hint was previously active, the hint's `highlightCells` take effect again
on the next hint interaction.

The two systems (hint + coach) do not run simultaneously. Opening the coach does not dismiss
an active hint, but it does override the visual highlight.

---

## Design Decisions

| Decision | Chosen | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Pre-analysis rather than tool loop | Deterministic engine runs first; result injected into prompt | LLM calls tools to analyse board | Eliminates hallucinated moves; one Bedrock call vs. 3–5; fits within 8s Lambda timeout |
| One Bedrock call per HTTP request | Single `InvokeModel` or `Converse` call (per `coach.bedrock.api-mode`) | Streaming or multi-step chain | Predictable latency; simpler error handling; Lambda timeout safety |
| Structured output enforced by Bedrock schema, not prompt wording alone | `output_config.format` (invoke) / `outputConfig.textFormat` (converse) constrain the reply to `{aiMessage, revealHint, responseType}` server-side | Keep relying solely on prompt instructions (`OUTPUT FORMAT — MANDATORY` wording) | Eliminates the prose-with-no-JSON fallback class entirely rather than reducing its frequency; live-spike-confirmed on both API modes against `eu.anthropic.claude-haiku-4-5-20251001-v1:0` |
| `responseType` categorical field added to the schema, logged only (not part of `CoachResponse`) | Enum of 7 values mapping 1:1 to the system prompt's existing pedagogical rules/examples; keeps the existing `aiMessage` field name unchanged | Rename `aiMessage`→`message` in lockstep (considered, rejected as unnecessary blast radius for this change); have the frontend also consume `responseType`; use an LLM judge or fuzzier prose-matching in the harness instead | A categorical field the model must choose from a fixed enum is far more reliable to assert on than substring-matching non-deterministic prose (the `wrong-guess-acknowledgment` scenario's `"double-check"` check was flaky for exactly this reason); keeping it log-only avoids touching `CoachResponse`, `CoachResource`, `SudokuCoachServiceImpl`, or any frontend file |
| Convert `nudge`/`focus`/`reveal` to 1-indexed in `buildContextBlock()` via a new `HintTextFormatter`, not by changing the hint strategies themselves | Java port of `hintDisplay.js`'s `formatHintText()`, applied only where the coach builds its prompt context | Make the hint strategies emit 1-indexed text directly (rejected — violates `HE-UI-005`'s "0-indexed internally, convert only at display layer" and would require re-deriving the frontend's now-redundant conversion too); have the frontend post-process the coach's `aiMessage` after the fact (rejected — the LLM has already committed to whatever numbers it read, so fixing the input is the only point that actually prevents wrong numbers reaching the player) | Fixes the reported bug (coach said "Row 7, Column 3" for a cell that is Row 8, Column 4 on the 1-indexed board) at its source — the LLM never sees a 0-indexed coordinate to repeat — without touching the 11 hint strategy files or the already-correct frontend hint dialog |
| Coach reveal auto-fills the board (`solvedCells`/`eliminatedCandidates` on `revealHint: true`), matching the Hint button | `useCoachSession.js` mirrors `useHintSystem.js`'s `advanceHint()` reveal-stage writes exactly | Keep the coach highlight-only, treating the final "type it yourself" step as an intentional part of guided discovery even after a reveal (considered, rejected) | The player reported confusion at the resulting state — the coach had already stated the answer in words, but the board didn't reflect it, unlike the equivalent Hint-button path. Consistency between the two reveal paths was judged more important than preserving a discovery-step distinction the player didn't perceive as intentional |
| `coach.bedrock.api-mode` flag (`invoke` default / `converse`), both modes schema-enforced | Config-driven dispatch in `BedrockCoachClient.call()` | Migrate outright to Converse; keep InvokeModel only | Both modes enforce the same schema so the A/B isolates API + caching differences from the reliability change, not the reliability change itself |
| Reveal coordinates never re-emitted by the LLM | Schema carries only `revealHint: boolean`; `HintResponse` (already returned in full) stays the sole source of cell/digit coordinates | Have the LLM restate the cell + digit in the schema | Avoids RULE 3 fabrication risk (LLM stating a wrong coordinate); keeps the enforced schema minimal; also unblocked SC-UI-050/051, later implemented, since the frontend can derive disclosure from `revealHint` + the already-present `HintResponse` fields |
| `converse` mode caches via `CachePointBlock`, `invoke` mode keeps `cache_control: {type: ephemeral}` | Per-API cache mechanism, both effectively an "ephemeral" 5-minute cache by default | Force both modes onto one cache mechanism | `cache_control` (Anthropic-native) is silently ignored by Converse; `cachePoint` is Converse-specific. Each API's own mechanism is used rather than fighting the SDK |
| `COACH_BEDROCK_API_MODE` Lambda env var defaults to `converse` on `rc-*` workspaces, `invoke` elsewhere (`infra/main.tf` `local.coach_bedrock_api_mode = local.is_rc ? "converse" : "invoke"`) | Keyed on `is_rc` specifically (not `!is_default`, unlike `ai_coach_enabled`/`VITE_AI_COACH`) | Mirror `ai_coach_enabled`'s `!is_default` condition exactly | RC is where new coach behaviour is A/B-tested before considering it for production. `!is_default` and `is_rc` are equivalent today (only `default`/`rc-*` workspaces exist), but this flag's intent is specifically "validate on RC" — keying on `is_rc` avoids a future non-RC workspace type silently inheriting an unvalidated api-mode. No IAM change needed — AWS's Converse API reuses the `bedrock:InvokeModel` permission the Lambda role already has |
| Frontend owns conversation history | Client sends last N turns each request | DynamoDB per-session storage | Stateless backend; no schema change; no extra DynamoDB read per request |
| Fallback to nudge text | `hint.nudge()` returned on Bedrock failure | Return 5xx on failure | Coaching endpoint always returns something useful; Bedrock unavailability doesn't break the game |
| Log full conversation content (userMessage, aiMessage, board, candidates) | INFO-level structured JSON, same CloudWatch log group as other Lambda logs, existing 30-day retention | Metadata-only logs; separate log store; log to a third-party sink | Personal project with a small, known, non-anonymous allowlisted user set — content logging is needed to review coaching quality and isn't a privacy risk under this threat model (see `docs/arrows/security-standards.md` Logging Policy); revisit if the app ever opens to anonymous users |
| Carry `pid` (gameId) on `COACH_REQUEST`/`COACH_RESPONSE` | `gameId` threaded from `CoachRequest` through the service into the log lines; null if the client omits it | Correlate coach and gameplay via `cid` only; a separate join table; require gameId (reject if absent) | `cid` only pairs a single request/response; `pid` is the session-wide key that joins coach turns with puzzle-play events for the same game. Logging null when absent keeps the coach usable outside a saved game rather than coupling it to game persistence |
| Build COACH_REQUEST/COACH_RESPONSE via `ObjectMapper`, not `LOG.infof` string templating | `objectMapper.writeValueAsString(...)` on an `ObjectNode` | Keep `%s`-templated strings; keep templating but add manual escaping (e.g. extend the existing `.replace("\"", "'")` pattern to more characters) | `userMessage`/`aiMessage` are the first freeform (user- or LLM-authored) fields in this log line; naive string substitution produces invalid JSON on embedded quotes/newlines, silently dropping the line from `jq`-based tooling downstream. A real serializer handles all escaping correctly instead of chasing individual special characters |
| Desktop only | `useMediaQuery` guard in `CoachWidget` | Bottom sheet on mobile | Board + chat can't coexist usefully on small screens; simpler to exclude mobile than to build a bad experience |
| Bottom-right floating widget | Fixed-position `Paper`, chat-window style | Side panel drawer | Zero layout disruption; board stays at full width; familiar pattern for users |

---

## Technical Debt / Open Questions

- **CRaC vs. GraalVM native vs. SnapStart**: Spike needed to confirm which cold-start strategy
  works best with the raw `BedrockRuntimeClient`. See §12 of `docs/planning/ai-guide.md`.
  **Concrete evidence this matters**: the coach's first invocation after a fresh deploy hit the
  Lambda's 8s hard timeout — `COACH_REQUEST` logged fine, but the Bedrock `InvokeModel` call
  never completed (`Status: timeout` in the Lambda REPORT line). The existing SnapStart warm-up
  only primes the HTTP layer (`GET /health`), not the Bedrock SDK client. A second call on the
  same (now-warm) execution environment completed in ~1.8–2.0s. Observed running
  `scripts/github/coach-smoke-test.sh` against a brand-new RC workspace's first-ever deploy.
- **System prompt caching threshold — resolved**: live-spike-confirmed against real Bedrock
  traffic that the actual minimum is **4,096 tokens**, not the 2,048/1,024 figures previously
  documented here. The pre-fix `SYSTEM_PROMPT` (~3,187 tokens) was *below* this threshold,
  which is why production showed `cacheRead=0 cacheWrite=0` across the entire coach-quality
  baseline (`ui/tests/coach-quality/reports/haiku-4-5-baseline/summary.txt`). Fixed by trimming
  the now-redundant `OUTPUT FORMAT`/`FINAL REMINDER` prose (superseded by server-enforced
  structured output) and re-padding above 4,096 tokens with genuinely useful few-shot content.
  Monitor cache hit metrics in CloudWatch logs (`cacheReadTokens`/`cacheWriteTokens` in
  `COACH_RESPONSE` log events) to confirm in production.
- **Caching economics depend on turns-per-conversation**: a single-turn interaction costs
  *more* with caching enabled (pays the write premium, gets no read) than without; break-even
  is ~2 turns within the 5-minute TTL. `tokensUsed` (charged against the 100k/month budget,
  SC-RL-002) is `inputTokens + outputTokens` only — cached-prefix tokens live in the separate
  `cacheRead`/`cacheWrite` fields and are *not* added to `tokensUsed`, so enabling caching
  silently shrinks the budget-tracked figure (players effectively get more coach turns per
  month for the same 100k-token cap). Kept token-based (not cost-weighted) for now — simpler,
  under-reflects the write premium, but the absolute spend is sub-cent/call regardless. Measure
  real turn distribution via the coach-quality harness before choosing 5m vs. 1h TTL or
  revisiting this budget-accounting choice.
- **Escape key panel close**: SC-UI-013 (Escape closes panel) is not implemented.
- **Rate limiter DynamoDB cost**: Each coach call writes to the `SudokuCoachRateLimits` table
  in addition to the main `SudokuPlayers` table. At low volume this is negligible; revisit
  if call volume grows.

---

## References

- HLD: `docs/planning/ai-guide.md`
- Existing hint engine: `docs/llds/hint-engine.md`
- Specs: `docs/specs/sudoku-coach-specs.md`
- Depends on: Sudoku Logic (Board, Cell), Hint Engine (SudokuService.getHint())
- Depends on: Amazon Bedrock (Claude Haiku) via raw `BedrockRuntimeClient` (LangChain4j was evaluated and rejected — it did not expose `cache_control` at the message level)
- Depended on by: Frontend (CoachWidget, useCoachSession)
- Key files:
  - `backend/.../coach/web/CoachResource.java`
  - `backend/.../coach/SudokuCoachServiceImpl.java`
  - `backend/.../coach/bedrock/BedrockCoachClient.java`
  - `backend/.../coach/bedrock/BoardFormatter.java`
  - `backend/.../coach/bedrock/HintTextFormatter.java` — 0→1-indexed conversion for the prompt context, ports `ui/src/utils/hintDisplay.js`
  - `backend/.../coach/bedrock/CoachRateLimiter.java`
  - `backend/.../puzzle/web/HintResponse.java` — not modified
  - `backend/.../domain/Board.java` — `fromGrid()`, `calculateAllCandidates()`
  - `ui/src/App.jsx` — mounts `CoachWidget` behind `VITE_AI_COACH` flag
  - `ui/src/components/coach/CoachWidget.jsx`, `CoachPanel.jsx`, `CoachMessage.jsx`
  - `ui/src/hooks/useCoachSession.js`
