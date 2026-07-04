# Sudoku Coach (AI Tutoring System)

**Created**: 2026-07-04
**Status**: Draft — not yet implemented
**HLD**: `docs/planning/ai-guide.md`
**Specs**: `docs/specs/sudoku-coach-specs.md` (to be created)

---

## Context and Current State

The Sudoku Coach is a conversational AI tutoring system for novice players. It teaches *why*
a logical move works, rather than just disclosing the answer. It is a new component that sits
alongside the existing deterministic hint engine — both share the same underlying strategy
chain, but the coach adds a Bedrock-powered coaching layer on top.

The coach is desktop-only. The UX requires the board and chat to be simultaneously visible,
which is not achievable on mobile screen sizes.

Nothing in the existing codebase changes. The coach is additive: new records, new service
interface, new service implementation, new endpoint, new IAM permission.

---

## Backend Architecture

### Component Map

```
PuzzleResource (or CoachResource)          ← REST adapter (input port)
        │  POST /puzzles/coach
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
LangChain4j / BedrockRuntimeClient         ← Claude Haiku via Amazon Bedrock
```

`SudokuCoachServiceImpl` orchestrates the deterministic pre-analysis. `BedrockCoachClient` is
the only class that knows about LangChain4j or Bedrock. This separation means:
- The deterministic backbone can be built and tested without any AI dependency.
- The Bedrock client can be swapped or mocked independently in tests.

### Interface

```java
public interface SudokuCoachService {
    CoachResponse coach(CoachRequest request);
}
```

### Orchestration Flow (`SudokuCoachServiceImpl.coach()`)

```
1. board = Board.fromGrid(request.board())
   board.calculateAllCandidates()

2. hint = sudokuService.getHint(BoardRequest.of(request.board()))
   // Uses existing hint engine — same chain as /puzzles/hint

3. if hint is empty:
       return CoachResponse.noMovesAvailable()  // no Bedrock call

4. context = BoardFormatter.format(board, hint)
   // Human-readable grid + technique name + relevant cells

5. history = trim(request.history(), MAX_HISTORY_MESSAGES)
   // Backend enforces cap; frontend may send more

6. aiMessage, revealHint = bedrockCoachClient.coach(context, history, request.userMessage())
   // Single Bedrock call; falls back to hint.nudge() on failure

7. return CoachResponse(aiMessage, hint, revealHint)
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

**Prompt caching:**
The system prompt is marked with `cache_control: {type: ephemeral}`. Bedrock caches the KV
state for a 5-minute TTL, reducing system prompt token cost by ~90% on cache hits. The system
prompt must be ≥1,024 tokens to be cacheable — include inline few-shot coaching examples to
reach this threshold if needed.

**LangChain4j vs. raw client:**
Use `BedrockRuntimeClient` directly if LangChain4j's Bedrock integration does not expose
`cache_control` blocks at the message level. Verify during Phase 3 spike.

### Constants

```java
public final class CoachConstants {
    public static final int MAX_HISTORY_MESSAGES = 6;  // ~600 tokens
    public static final int BEDROCK_TIMEOUT_SECONDS = 6; // leaves 2s headroom vs. 8s Lambda limit
}
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
POST /puzzles/coach
Authorization: Bearer {cognito-jwt}
Content-Type: application/json

Request:  CoachRequest
Response: 200 CoachResponse   — coaching response (AI or fallback)
          204                 — puzzle already solved, no technique applicable
          400                 — malformed board or blank userMessage
          401                 — missing or invalid JWT (enforced by API Gateway)
```

A `204` is returned only when `getHint()` returns empty because the board is solved. The
endpoint never returns a 5xx for a Bedrock failure — it degrades to the fallback response
and returns 200.

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
| Desktop only | `useMediaQuery` guard in `CoachWidget` | Bottom sheet on mobile | Board + chat can't coexist usefully on small screens; simpler to exclude mobile than to build a bad experience |
| Bottom-right floating widget | Fixed-position `Paper`, chat-window style | Side panel drawer | Zero layout disruption; board stays at full width; familiar pattern for users |

---

## Technical Debt / Open Questions

- **CRaC vs. GraalVM native vs. SnapStart**: Spike needed before Phase 4 to confirm which
  cold-start strategy works with LangChain4j. See §12 of `docs/planning/ai-guide.md`.
- **LangChain4j `cache_control` support**: May require dropping to raw `BedrockRuntimeClient`.
  Determine in Phase 4 spike.
- **System prompt length**: Must be ≥1,024 tokens for Bedrock caching. If the base prompt is
  shorter, few-shot examples must be added. Measure at implementation time.
- **`revealHint` reliability**: The LLM must consistently set `revealHint: true` only when
  explicitly stating the answer. System prompt wording and few-shot examples are the controls.
  Monitor in testing and refine the prompt if the signal is unreliable.

---

## References

- HLD: `docs/planning/ai-guide.md`
- Existing hint engine: `docs/llds/hint-engine.md`
- Specs (to create): `docs/specs/sudoku-coach-specs.md`
- Depends on: Sudoku Logic (Board, Cell), Hint Engine (SudokuService.getHint())
- Depends on: Amazon Bedrock (Claude Haiku), LangChain4j
- Depended on by: Frontend (CoachWidget, useCoachSession)
- Key existing files:
  - `backend/.../dto/HintResponse.java` — not modified
  - `backend/.../puzzle/SudokuService.java` — add `coach()` or use existing `getHint()`
  - `backend/.../domain/Board.java` — `fromGrid()`, `calculateAllCandidates()`
  - `ui/src/App.jsx` — mount `CoachWidget` alongside existing modals
