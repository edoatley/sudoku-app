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

7. return CoachResult.Response(CoachResponse(result.reply, hint, result.revealHint), result.tokensUsed)
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
4. Making a single Bedrock call
5. Parsing the structured JSON response `{ "aiMessage": "...", "revealHint": true|false }`
6. Falling back to `hint.nudge()` + `revealHint=false` on timeout or parse failure
7. Logging a structured `COACH_REQUEST`/`COACH_RESPONSE` JSON line per turn, correlated by `cid`

**Prompt caching:**
The system prompt is marked with `cache_control: {type: ephemeral}`. Bedrock caches the KV
state for a 5-minute TTL, reducing system prompt token cost by ~90% on cache hits. The system
prompt must be ≥1,024 tokens to be cacheable — include inline few-shot coaching examples to
reach this threshold if needed.

**Raw Bedrock client:**
`BedrockRuntimeClient` is used directly (not LangChain4j). LangChain4j did not expose
`cache_control` blocks at the message level, so the raw SDK was chosen.

**Content logging:**
Each call to `BedrockCoachClient.call()` emits two structured JSON lines to the standard
Quarkus/Lambda logger, sharing a `cid` (UUID, generated per call) so the pair can be joined.
Lines are built via `objectMapper.writeValueAsString(...)` (the already-injected `ObjectMapper`)
rather than the hand-templated `LOG.infof("{\"...\":\"%s\"}", ...)` string substitution used
today — `userMessage` and `aiMessage` are the first freeform, user-/LLM-authored text this
logging handles, and naive `%s` substitution breaks on embedded quotes or newlines (the
existing exception-path `errorMsg` field already works around this narrowly, via
`.replace("\"", "'")`, for the one freeform field that existed before this change — a proper
serializer replaces that workaround too, correctly, for all fields):

```
COACH_REQUEST  { type, cid, modelId, technique, historyLen, userMsgLen, ts,
                  userMessage, board, candidatesGrid }
COACH_RESPONSE { type, cid, revealHint, inputTokens, outputTokens,
                  cacheReadTokens, cacheWriteTokens, latencyMs, fallback,
                  aiMessage }
```

`board` is the `Board`'s placed digits (row-major, not the wire-format `Grid` object — built
inline from `Board.getRow()`) and `candidatesGrid` is `Board.toCandidatesGrid()`. Both are
derived from the single `Board` computed once in `SudokuCoachServiceImpl.coach()` (see
Orchestration Flow step 3) and passed through, not recomputed here. `aiMessage` is logged on **every** path, including the
`fallback: true` path — it is the deterministic nudge text there rather than an actual Bedrock
response, but it is still what the player saw, and logging it keeps `COACH_RESPONSE` a
complete record of "what did the coach actually say" regardless of which path produced it.

These logs go to the **same CloudWatch log group** as all other Lambda logs
(`/aws/lambda/sudoku{suffix}`), at the existing 30-day retention — no separate log store, per
`docs/arrows/security-standards.md` Logging Policy. That policy already sanctions logging full
coach conversation content (userMessage, aiMessage, board, candidatesGrid) for this app's
threat model: a personal project with a small, known, non-anonymous allowlisted user set.

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
    boolean revealHint
) {}
```

| Field | Type | Notes |
|-------|------|-------|
| `aiMessage` | `String` | Coaching prose from LLM (or fallback nudge text) |
| `hint` | `HintResponse` | Full deterministic hint — unchanged existing DTO |
| `revealHint` | `boolean` | Frontend uses this to control whether to show `hint.reveal`, `solvedCells`, `eliminatedCandidates` |

`HintResponse` is returned in full. The frontend decides which fields to show based on
`revealHint` and its own conversation state. The backend never partially populates it.

---

## System Prompt (Tutor Persona)

Stored as a constant in `BedrockCoachClient` (or an injected `@ConfigProperty` string).
Must be ≥1,024 tokens to qualify for prompt caching. The required elements:

1. **Persona**: Patient Sudoku tutor. Encouraging, never condescending.
2. **Constraint**: Do not invent moves. The board state and applicable technique are provided
   as context — refer only to cells and digits explicitly present in that context.
3. **Pedagogy rule**: Ask questions before giving answers. Guide the player to the insight.
   After 2–3 turns on the same technique without progress, disclose more.
4. **Output schema**: Return ONLY valid JSON: `{ "aiMessage": "...", "revealHint": true|false }`.
   `revealHint` is `true` only if the message explicitly states the cell and digit.
5. **Few-shot examples** (to reach ≥1,024 token threshold and improve output quality):
   Include 2–3 example coaching exchanges showing good escalation behaviour.

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

`CoachWidget` receives `setHighlightCells` as a prop from `App.jsx` (same setter already
used by `useHintSystem`). When a coach response arrives, `useCoachSession` calls
`setHighlightCells(response.hint.highlightCells)`. This updates the board immediately —
the player sees the relevant cells highlighted while the AI message is visible.

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
| One Bedrock call per HTTP request | Single `InvokeModel` call | Streaming or multi-step chain | Predictable latency; simpler error handling; Lambda timeout safety |
| Frontend owns conversation history | Client sends last N turns each request | DynamoDB per-session storage | Stateless backend; no schema change; no extra DynamoDB read per request |
| Fallback to nudge text | `hint.nudge()` returned on Bedrock failure | Return 5xx on failure | Coaching endpoint always returns something useful; Bedrock unavailability doesn't break the game |
| Log full conversation content (userMessage, aiMessage, board, candidates) | INFO-level structured JSON, same CloudWatch log group as other Lambda logs, existing 30-day retention | Metadata-only logs; separate log store; log to a third-party sink | Personal project with a small, known, non-anonymous allowlisted user set — content logging is needed to review coaching quality and isn't a privacy risk under this threat model (see `docs/arrows/security-standards.md` Logging Policy); revisit if the app ever opens to anonymous users |
| Build COACH_REQUEST/COACH_RESPONSE via `ObjectMapper`, not `LOG.infof` string templating | `objectMapper.writeValueAsString(...)` on an `ObjectNode` | Keep `%s`-templated strings; keep templating but add manual escaping (e.g. extend the existing `.replace("\"", "'")` pattern to more characters) | `userMessage`/`aiMessage` are the first freeform (user- or LLM-authored) fields in this log line; naive string substitution produces invalid JSON on embedded quotes/newlines, silently dropping the line from `jq`-based tooling downstream. A real serializer handles all escaping correctly instead of chasing individual special characters |
| Desktop only | `useMediaQuery` guard in `CoachWidget` | Bottom sheet on mobile | Board + chat can't coexist usefully on small screens; simpler to exclude mobile than to build a bad experience |
| Bottom-right floating widget | Fixed-position `Paper`, chat-window style | Side panel drawer | Zero layout disruption; board stays at full width; familiar pattern for users |

---

## Technical Debt / Open Questions

- **CRaC vs. GraalVM native vs. SnapStart**: Spike needed to confirm which cold-start strategy
  works best with the raw `BedrockRuntimeClient`. See §12 of `docs/planning/ai-guide.md`.
- **System prompt caching threshold**: Claude Haiku models require ≥2,048 tokens (not 1,024)
  for prompt caching. The current `SYSTEM_PROMPT` exceeds this. Monitor cache hit metrics in
  CloudWatch logs (`cacheReadTokens` / `cacheWriteTokens` in COACH_RESPONSE log events).
- **`revealHint` frontend handling**: The frontend does not yet act on `revealHint` — it
  displays the AI message but never conditionally shows/hides hint fields. Specs SC-UI-050
  and SC-UI-051 remain unimplemented.
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
  - `backend/.../coach/bedrock/CoachRateLimiter.java`
  - `backend/.../puzzle/web/HintResponse.java` — not modified
  - `backend/.../domain/Board.java` — `fromGrid()`, `calculateAllCandidates()`
  - `ui/src/App.jsx` — mounts `CoachWidget` behind `VITE_AI_COACH` flag
  - `ui/src/components/coach/CoachWidget.jsx`, `CoachPanel.jsx`, `CoachMessage.jsx`
  - `ui/src/hooks/useCoachSession.js`
