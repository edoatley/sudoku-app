# High-Level Design: Sudoku Coach (AI Tutoring System)

**Status**: Draft — awaiting LLD and EARS
**Created**: 2026-07-04

---

## 1. System Objective

Build a conversational AI system that teaches novice Sudoku players *how* to solve a puzzle,
not just what to do next. The AI walks the player through the reasoning behind each logical
technique, responding to their questions and adapting how much to reveal based on the
conversation so far.

This is distinct from the existing deterministic hint engine (`/puzzles/hint`), which returns
a structured hint the UI reveals in stages. The coach is a multi-turn dialogue.

---

## 2. Design Principles

- **One Bedrock call per player message.** Predictable latency; fits within the 8s Lambda timeout.
- **Deterministic analysis first, LLM prose second.** The existing hint engine identifies the
  applicable technique; the LLM only writes the coaching message. This eliminates hallucinated moves.
- **Desktop only.** The coaching experience requires the board and chat to be simultaneously
  visible. On mobile there is not enough screen real estate to achieve this without a poor
  compromise. The coach is not rendered on small viewports.
- **Reuse, don't reimplement.** All Sudoku logic lives in the existing `puzzle/hint/` strategies
  and `domain/Board`. The AI layer wraps these; it never duplicates them.
- **Stateless backend.** The frontend sends the last N conversation turns with every request.
  No DynamoDB writes per coaching turn.
- **AI decides disclosure.** The LLM reads the conversation and judges how much to reveal.
  No explicit escalation levels from the player.

---

## 3. Technology Stack

| Layer        | Technology                                    |
| ------------ | --------------------------------------------- |
| Runtime      | Java 21 / Quarkus (existing Lambda)           |
| Compute      | AWS Lambda with Quarkus SnapStart             |
| LLM          | Amazon Bedrock — Claude Haiku                 |
| LLM Client   | LangChain4j (quarkus-langchain4j-bedrock)     |
| Architecture | Hexagonal / Clean (matches existing codebase) |
| Auth         | Cognito JWT via existing API Gateway enforcer |

**Model choice**: Claude Haiku. Already used by the image-recognition Lambda; consistent IAM
policy, lowest latency in the Anthropic family, sufficient reasoning for Sudoku techniques.

**No GraalVM native compilation.** The existing backend uses Quarkus SnapStart. LangChain4j's
dynamic proxies are difficult to compile to native binary and would require extensive
`reflect-config.json` work. SnapStart gives cold-start performance without that risk.

---

## 4. Architecture: Stateless-per-Request Coaching

### 4.1 What changes vs. the existing hint endpoint

The existing `POST /puzzles/hint` is a deterministic, fast (<100ms) operation. It stays
unchanged. The new `POST /puzzles/coach` sits alongside it and adds Bedrock-backed coaching.

Both endpoints share the same underlying hint engine.

```
/puzzles/hint   → SudokuServiceImpl → HintStrategy chain → HintResponse
/puzzles/coach  → SudokuCoachServiceImpl → HintStrategy chain
                                        → Bedrock (Claude Haiku)
                                        → CoachResponse
```

### 4.2 Processing flow

```
CoachRequest (board, history, userMessage)
        │
        ▼
1. Board.fromGrid(board) + calculateAllCandidates()
        │
        ▼
2. Existing hint engine → Optional<HintResponse>
   ├── empty → CoachResponse with deterministic "no moves left" message (skip Bedrock)
   └── present → technique context available
        │
        ▼
3. Build human-readable board string (row-by-row with separators, not deepToString)
        │
        ▼
4. Build Bedrock prompt:
   - System prompt: tutor persona, pedagogy rules, output schema
   - Context block: board, technique name, relevant cells (from HintResponse)
   - Conversation history: last 6 messages (trimmed by backend if over limit)
   - User turn: userMessage
        │
        ▼
5. One Bedrock call → structured JSON
        │
        ▼
6. CoachResponse {
       aiMessage: String,       // coaching prose
       hint: HintResponse,      // deterministic hint (for cell highlighting)
       revealHint: boolean       // AI signalled it's disclosing the answer
   }
```

### 4.3 Why pre-analysis rather than tool-calling loop

The HLD originally proposed giving the LLM tools (`getValidCandidates`, `findNakedSingle`)
and letting it decide how many tool calls to make. This is rejected for three reasons:

1. **Timeout risk.** Each Bedrock roundtrip costs 500ms–2s. A 3-step tool loop risks
   exceeding the 8s Lambda timeout.
2. **Hallucination risk.** The LLM can and does call tools with incorrect coordinates,
   especially on partially filled boards. Pre-analysis eliminates this entirely.
3. **Token cost.** A tool loop sends the board state multiple times. Pre-analysis sends it once.

The pre-analysis pattern gives the LLM everything it needs as read-only context.

---

## 5. Data Structures

### 5.1 Request

```java
public record CoachRequest(
    Grid board,                        // 9×9 board (existing wire type, not int[][])
    List<ChatMessage> history,         // last ≤6 turns from the frontend
    String userMessage                 // what the player just said
) {}

public record ChatMessage(
    String role,                       // "user" or "assistant"
    String content
) {}
```

`Grid` is `List<List<Integer>>` wrapped as `{"rows": [...]}` — consistent with all existing
endpoints. Never use `int[][]` at the API boundary.

### 5.2 Response

```java
public record CoachResponse(
    String aiMessage,                  // coaching prose from the LLM
    HintResponse hint,                 // existing deterministic hint (cell highlights etc.)
    boolean revealHint                 // true if AI chose to disclose the solution
) {}
```

`HintResponse` is the existing DTO — unchanged. The frontend already knows how to render it.
When `revealHint` is false, the frontend shows `aiMessage` but does not show `hint.reveal`,
`hint.solvedCells`, or `hint.eliminatedCandidates`. When `revealHint` is true, it shows everything.

---

## 6. Token Budget and Prompt Caching

### 6.1 Per-request token estimate

| Component                        | Estimated tokens | Cacheable? |
| -------------------------------- | ---------------- | ---------- |
| System prompt (tutor persona)    | ~400             | Yes        |
| Board (readable 9×9 grid)        | ~200             | No         |
| Applicable technique context     | ~150             | No         |
| Conversation history (6 msgs)    | ~600             | No         |
| User message                     | ~50              | No         |
| **Total input**                  | **~1,400**       |            |
| **Output (coaching message)**    | **~200**         |            |
| **Cost per call (Claude Haiku)** | **~$0.0004**     |            |

Conversation history cap (6 messages) is enforced server-side. If the frontend sends more,
the backend trims to the last 6 before building the prompt.

### 6.2 Prompt caching

Amazon Bedrock supports Claude's prompt caching via `cache_control` blocks. The system prompt
is identical across all coaching requests — it is the primary cache target.

**How it works:**

Mark the system prompt block with `"cache_control": {"type": "ephemeral"}`. Bedrock caches
the KV state of those tokens for a 5-minute TTL (extended on each hit). On a cache hit,
the cached system prompt tokens are billed at ~10% of normal input cost.

**Cache hit analysis:**

For a novice player in an active session, turn-to-turn gaps are typically under 5 minutes.
The system prompt (~400 tokens) is cached after the first call in each session window.

| Scenario                          | Cache hit? | System prompt cost |
| --------------------------------- | ---------- | ------------------ |
| Same player, <5 min between turns | Yes        | ~$0.000004         |
| First call or >5 min gap          | No         | ~$0.00004          |

The board state, technique context, history, and user message are never cached — they change
every turn.

**Implementation approach in LangChain4j:**

LangChain4j's Bedrock integration may not expose `cache_control` directly via `@SystemMessage`.
If so, use the raw Bedrock `InvokeModel` API via `BedrockRuntimeClient` with the Anthropic
messages API format, and construct the request body manually with cache blocks. This is more
verbose but gives full control over the cache points.

Verify LangChain4j version support for Bedrock prompt caching before committing to the
higher-level abstraction.

**Minimum token threshold:**

Bedrock only caches blocks of ≥1,024 tokens. If the system prompt is under this threshold,
pad it with inline few-shot examples (good coaching dialogues) to push it over 1,024 tokens.
This is a win-win: richer examples improve coaching quality and enable caching.

---

## 7. System Prompt (Tutor Persona)

The system prompt gives the LLM its pedagogical role and output format. Key elements:

- You are a patient Sudoku tutor helping a beginner.
- The board state and the applicable technique have been pre-analysed and are provided as
  context. Do not invent moves or cells not in the technique context.
- Guide the player toward the insight — do not immediately state the answer.
- After 2–3 turns on the same technique, you may reveal more if the player is still stuck.
- Output **only** valid JSON matching `{ "aiMessage": "...", "revealHint": true|false }`.
- `revealHint` should be `true` only when you explicitly tell the player the cell and digit.

---

## 8. Security

The `/puzzles/coach` endpoint must:
- Require a valid Cognito JWT (enforced at API Gateway, same as all game endpoints)
- Extract `userId` from `SecurityContext` (as all resource classes already do)
- Validate `board` input the same way as `POST /puzzles/hint` (not null, 9×9, digits 0–9)
- Cap `history` at 6 messages server-side (input validation boundary)

No IDOR risk on this endpoint — the board is passed in the payload, not a game ID. There is
no database read; the userId is used only for logging/audit if needed.

---

## 9. IAM

The Java Lambda execution role currently has no Bedrock permissions. Add:

```json
{
  "Effect": "Allow",
  "Action": "bedrock:InvokeModel",
  "Resource": "arn:aws:bedrock:eu-west-2::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"
}
```

This is consistent with the pattern already in place for the image-recognition Lambda.

---

## 10. Fallback Behaviour

| Scenario                           | Behaviour                                                               |
| ---------------------------------- | ----------------------------------------------------------------------- |
| No technique found (puzzle solved) | Return `CoachResponse` with deterministic message, no Bedrock call      |
| No technique found (stuck)         | Return `CoachResponse` with "no more hints" message, no Bedrock call    |
| Bedrock timeout (>6s)              | Return existing `HintResponse.nudge` as `aiMessage`, `revealHint=false` |
| Bedrock returns malformed JSON     | Same fallback as timeout                                                |

The fallback ensures the endpoint always returns a usable response, degrading gracefully
to the deterministic nudge text.

---

## 11. Tradeoffs

| Concern                                        | Decision                             | Reasoning                                          |
| ---------------------------------------------- | ------------------------------------ | -------------------------------------------------- |
| Tool-calling vs. pre-analysis                  | Pre-analysis (deterministic first)   | Eliminates hallucination and timeout risk          |
| Native vs. SnapStart                           | SnapStart (for now — see TODO below) | LangChain4j proxy compatibility; existing platform |
| History in DynamoDB vs. frontend               | Frontend sends history               | Stateless backend; no schema change needed         |
| Replace vs. extend `/puzzles/hint`             | Extend (new endpoint)                | No breaking change; coaching is opt-in             |
| Player-controlled vs. AI-controlled disclosure | AI-controlled                        | More natural tutoring; LLM reads the conversation  |
| Chat memory window                             | 6 messages                           | ~600 tokens; covers 3 full exchanges               |

---

## 12. Open Questions / TODOs

### TODO: Evaluate CRaC vs. GraalVM Native vs. Quarkus SnapStart

Three options exist for eliminating Lambda cold starts. The right choice depends on how well
LangChain4j's dynamic proxies behave under each. Evaluate before finalising the deployment
approach.

| Option                                       | Cold start     | Complexity                                                                                                        | LangChain4j risk                                                   |
| -------------------------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| **Quarkus SnapStart** (current)              | ~200ms restore | Low — no code changes                                                                                             | Low — runs on standard JVM                                         |
| **GraalVM native-image**                     | <50ms          | High — `reflect-config.json` for every proxy/reflection use in LangChain4j; potential for subtle runtime failures | High — dynamic proxy generation breaks native mode                 |
| **CRaC** (Coordinated Restore at Checkpoint) | ~100ms         | Medium — requires CRaC-enabled JDK (Azul Zulu CRaC build or OpenJDK CRaC); `Resource` hooks for open file handles | Low-medium — runs on standard JVM; same class loading as SnapStart |

**Recommendation to evaluate:**

1. Spike GraalVM native with a minimal LangChain4j + Bedrock invocation. If `reflect-config.json`
   can be generated fully by the Quarkus native agent and all tests pass in native mode, it is
   viable. If there are unresolvable proxy failures, rule it out.
2. Spike CRaC as an alternative to SnapStart. CRaC checkpoints are taken in the dev/build
   pipeline (not by AWS), so it is not Lambda-native — but Quarkus has a CRaC extension.
   Check whether AWS Lambda supports CRaC snapshots or whether this is only useful for
   container-based deployments (ECS/Fargate).
3. If neither spike succeeds within one day, stay on SnapStart.

Add findings to `docs/llds/sudoku-coach.md` before implementation begins.

---

## 14. Frontend Design (Desktop Only)

### 14.1 Approach: Floating Chat Widget

The coach is a fixed-position chat window anchored to the bottom-right corner of the
viewport — the same pattern used by Intercom, Zendesk Chat, and similar support tools.
The board layout is completely unaffected: no column shifts, no width changes, no overlapping
the grid. The widget floats above the page in its own stacking context.

**Why not a side panel or modal:**

A side panel narrows the board. A modal hides it entirely. Both break the core requirement
of seeing board and chat simultaneously. The bottom-right widget avoids both problems —
the board occupies the full centre of the page while the chat sits in the corner, which on a
typical desktop viewport is empty space below the number pad.

**Desktop only:**

The widget is rendered only at `md` breakpoint and above (≥900px, matching MUI's default `md`).
On smaller viewports the coach button is hidden and the panel never mounts. Mobile coaching
is out of scope.

### 14.2 Visual Structure

```
┌────────────────────────────────────────────────────────┐
│  Header                                                │
├────────────────────────────────────────────────────────┤
│                                                        │
│          [ Sudoku Grid ]                 ┌───────────┐ │
│                                          │ Coach     │ │
│          [ Number Pad ]                  │───────────│ │
│                                          │ AI msg   ↑│ │
│          [ Hint Panel ]                  │           │ │
│                                          │ User msg  │ │
│                                          │           │ │
│                                          │ AI msg   ↓│ │
│                                          │───────────│ │
│                                          │ [input] ➤ │ │
│                                          └───────────┘ │
│                                        [💬 Coach btn]  │
└────────────────────────────────────────────────────────┘
```

When collapsed, only a FAB-style "Coach" button is visible (bottom-right). Clicking it
expands the chat panel upward. Clicking again or pressing Escape collapses it.

### 14.3 Chat Widget Anatomy

```
┌─────────────────────────────────┐  ← Paper, elevation=4, fixed position
│ 🤖 Sudoku Coach          [✕]    │  ← Header bar with title and close button
├─────────────────────────────────┤
│                                 │  ← Message list (overflow-y: auto)
│  ┌─────────────────────────┐    │
│  │ Welcome! I can see      │    │  ← AI bubble (left-aligned, grey bg)
│  │ you're working on a     │    │
│  │ Medium puzzle...        │    │
│  └─────────────────────────┘    │
│                                 │
│          ┌──────────────────┐   │
│          │ I'm really stuck │   │  ← User bubble (right-aligned, primary bg)
│          └──────────────────┘   │
│                                 │
│  ┌─────────────────────────┐    │
│  │ ● ● ●                   │    │  ← Typing indicator (while loading)
│  └─────────────────────────┘    │
├─────────────────────────────────┤
│  [I'm stuck] [Tell me more]     │  ← Quick reply chips
├─────────────────────────────────┤
│  [Type a message...      ] [➤]  │  ← Input row
└─────────────────────────────────┘
```

**Dimensions**: ~360px wide, ~460px tall. Fixed position: `bottom: 80px, right: 24px`.
The "Coach" FAB button sits at `bottom: 24px, right: 24px` below it.

### 14.4 Board-Chat Linkage

When the coach response includes `hint.highlightCells`, those cells are highlighted on the
board immediately — without the player having to close the chat. This is implemented by
having the `useCoachSession` hook write to the same `highlightCells` state that `useHintSystem`
already writes to. The two systems must not conflict: opening the coach clears any active
hint highlight; closing the coach restores any previous hint highlight.

### 14.5 Quick Reply Chips

Three shortcut chips appear below the message list:

| Chip label            | Sent as userMessage                            |
| --------------------- | ---------------------------------------------- |
| "I'm stuck"           | "I'm stuck and not sure what to look at next." |
| "Tell me more"        | "Can you give me a bit more of a clue?"        |
| "Why does that work?" | "Can you explain why that technique works?"    |

Chips are hidden while a response is loading.

### 14.6 Welcome Message

When the panel opens for the first time in a game session, fire an immediate API call with:

```
userMessage: "I just opened the coach. Say hello briefly and let me know what you can see."
```

This avoids the blank-chat problem. The AI greets the player and acknowledges the current
board state. The player doesn't need to type anything to get started.

### 14.7 Conversation Lifecycle

| Event                  | Effect on conversation history                |
| ---------------------- | --------------------------------------------- |
| Player opens coach     | History empty → welcome message fires         |
| Player sends a message | Message appended, API call fired              |
| Player makes a move    | History unchanged (coach stays in context)    |
| New game started       | History cleared, coach closed                 |
| Player closes coach    | History preserved (reopening resumes session) |

### 14.8 Frontend Component Plan

| Component / Hook     | Responsibility                                               |
| -------------------- | ------------------------------------------------------------ |
| `CoachWidget.jsx`    | Outer container: FAB + collapsible panel, desktop-only guard |
| `CoachPanel.jsx`     | Chat panel layout: header, message list, chips, input row    |
| `CoachMessage.jsx`   | Individual message bubble (AI or user variant)               |
| `useCoachSession.js` | State: history array, loading flag, API call, history trim   |

`CoachWidget` is mounted once in `App.jsx` alongside the existing `HintDialog` and modals.
It reads `currentGrid` (for the board payload) from the existing game state — no new prop
drilling beyond what is already passed at the top level.

---

## 15. What Is Not In Scope

- Storing conversation history in DynamoDB
- Coach UI on mobile viewports
- Player-initiated escalation levels (nudge / focus / reveal)
- Replacing the existing deterministic `/puzzles/hint` endpoint
- Supporting models other than Claude Haiku at launch

---

## 16. Implementation Checklist

### Backend

- [ ] **Spike**: Evaluate CRaC vs. GraalVM native vs. SnapStart with LangChain4j (see §12 TODO)
- [ ] Add `quarkus-langchain4j-bedrock` extension to `backend/pom.xml`
- [ ] Verify chosen startup strategy compatibility with LangChain4j (integration test)
- [ ] **Spike**: Confirm LangChain4j Bedrock integration supports `cache_control` blocks; fall back to raw `BedrockRuntimeClient` if not
- [ ] `CoachRequest` record: `Grid board`, `List<ChatMessage> history`, `String userMessage`
- [ ] `ChatMessage` record: `String role`, `String content`
- [ ] `CoachResponse` record: `String aiMessage`, `HintResponse hint`, `boolean revealHint`
- [ ] `SudokuCoachService` interface with `coach(CoachRequest): CoachResponse`
- [ ] `BedrockSudokuCoachServiceImpl`:
  - Calls `getHint()` on existing service to get technique context
  - Formats board as human-readable string (not `deepToString`)
  - Trims history to last 6 messages
  - Builds LangChain4j prompt with system message
  - One Bedrock call → parse JSON → return `CoachResponse`
  - Fallback on timeout or parse failure
- [ ] `POST /puzzles/coach` in `PuzzleResource` (or new `CoachResource`)
- [ ] Input validation: board not null, 9×9, digits 0–9; history ≤6 messages; userMessage not blank
- [ ] Bedrock IAM permission on Java Lambda role (Terraform)
- [ ] Unit tests for board formatter, history trimmer, prompt builder
- [ ] Unit tests for `BedrockSudokuCoachServiceImpl` (mock LangChain4j model)
- [ ] `@QuarkusTest` for `POST /puzzles/coach` endpoint

### Frontend

- [ ] `CoachWidget.jsx` — FAB + collapsible panel; desktop-only guard (`useMediaQuery`)
- [ ] `CoachPanel.jsx` — header, message list, quick reply chips, input row
- [ ] `CoachMessage.jsx` — AI (left) and user (right) bubble variants, typing indicator
- [ ] `useCoachSession.js` — history array, loading state, `sendMessage()`, history trim to 6, welcome message on open
- [ ] Wire `CoachWidget` into `App.jsx` alongside existing modals; pass `currentGrid` and game state
- [ ] Board-chat linkage: `useCoachSession` writes to shared `highlightCells` state; clears on close, restores previous hint highlight
- [ ] Clear coach history on new game event
- [ ] Desktop-only: no coach button or panel rendered at `< md` breakpoint

### Documentation

- [ ] Create `docs/llds/sudoku-coach.md`
- [ ] Create `docs/specs/sudoku-coach-specs.md` (EARS format)
- [ ] Update `docs/high-level-design.md` with Sudoku Coach component
- [ ] Add arrow to `docs/arrows/index.yaml`

---

## 17. Delivery Phases

Each phase is independently deployable and testable. Phases have a dependency order but each
adds incremental, verifiable value. No phase produces a dead branch that only works when a
later phase lands.

---

### Phase 1 — API Contract and Stub (Backend)

**What**: OpenAPI spec updated; Java records created (`CoachRequest`, `ChatMessage`,
`CoachResponse`); stub `POST /puzzles/coach` returns a hardcoded `CoachResponse`.

**Deploy**: Backend Lambda (existing deployment pipeline).

**Test**:
```bash
# Verify the endpoint exists and returns the correct shape
curl -X POST /api/v1/puzzles/coach \
  -H "Authorization: Bearer {token}" \
  -d '{"board": [...], "history": [], "userMessage": "hello"}'
# Expect: 200 with CoachResponse fields present
```
`@QuarkusTest` for the endpoint shape. Frontend can begin widget development against this
stub immediately.

---

### Phase 2 — Deterministic Backbone (Backend)

**What**: `SudokuCoachService` interface; `SudokuCoachServiceImpl` that calls the existing
hint engine and returns the hint's `nudge` text as `aiMessage`. No Bedrock. No LangChain4j.
`BoardFormatter` utility. History trimming. Input validation. Fallback for solved board (204).

**Deploy**: Backend Lambda — the endpoint now returns real, board-aware coaching text.

**Test**:
```bash
# POST a real board state; verify aiMessage reflects the actual board position
# and hint.highlightCells contains valid coordinates
```
Unit tests for `BoardFormatter`, history trimmer, service logic.
`@QuarkusTest` for validation rules (malformed board → 400, solved board → 204).

At the end of this phase the endpoint is fully functional as a hint-paraphrase service,
even without AI. The frontend can wire up against it.

---

### Phase 3 — Infrastructure: Bedrock IAM (Terraform)

**What**: Add `bedrock:InvokeModel` permission scoped to the Claude Haiku model ARN to the
Java Lambda execution role.

**Deploy**: `terraform apply` in the `infra/` directory.

**Test**:
```bash
terraform plan  # shows exactly one IAM statement added, no other changes
terraform apply
# Verify Lambda can call Bedrock (will be validated functionally in Phase 4)
```

This is a prerequisite for Phase 4 but is a one-liner Terraform change that can be reviewed
and applied independently.

---

### Phase 4 — AI Integration (Backend)

**What**: Add `quarkus-langchain4j-bedrock` to `pom.xml`. Spike: confirm SnapStart
compatibility and `cache_control` support (raw `BedrockRuntimeClient` fallback if needed).
`BedrockCoachClient` implementation with prompt caching. System prompt with few-shot examples.
Fallback to `hint.nudge()` on Bedrock timeout or JSON parse failure.

**Deploy**: Backend Lambda — the endpoint now returns real AI coaching messages.

**Test**:
- Unit tests with mocked `BedrockRuntimeClient` (verify prompt structure, fallback behaviour)
- Integration test with real Bedrock call (gated behind `@Tag("bedrock")`, opt-in in CI)
- Manual: POST a stuck board, verify the AI response is pedagogically useful and doesn't
  hallucinate moves

---

### Phase 5 — Frontend Widget Shell (Frontend)

**What**: `CoachWidget.jsx`, `CoachPanel.jsx`, `CoachMessage.jsx`. Desktop-only guard.
`useCoachSession.js` wired to mock API (`VITE_MOCK_API=true`). Quick reply chips. Typing
indicator. FAB open/close behaviour.

**Deploy**: Amplify frontend deploy.

**Test**:
- Open the app on desktop → Coach button visible
- Open the app on mobile → Coach button absent
- Click Coach → panel opens bottom-right, board fully visible
- Type a message → appears as user bubble; mock response appears as AI bubble
- Quick reply chips → send the preset message

---

### Phase 6 — Frontend Integration and E2E (Frontend + Full Stack)

**What**: Wire `useCoachSession.js` to the real `/api/v1/puzzles/coach` endpoint.
Board-chat linkage (`setHighlightCells` on AI response). Welcome message on first open.
Conversation history cleared on new game. Playwright E2E test covering the full coaching flow.

**Deploy**: Amplify frontend deploy — full feature complete end-to-end.

**Test**:
- Playwright: open coach on a stuck board → verify welcome message arrives, board cells
  highlighted match `hint.highlightCells`
- Playwright: send "I'm stuck" → verify AI response and updated highlights
- Playwright: start new game → verify coach history cleared and panel closed

---

### Dependency Graph

```
Phase 1 (stub)
    └── Phase 2 (deterministic) ──┬── Phase 5 (frontend shell, mock)
                                  │       └── Phase 6 (E2E integration)
Phase 3 (IAM) ────────────────────┘
    └── Phase 4 (AI)
```

Phases 3 and 2 can proceed in parallel. Phase 5 can begin as soon as Phase 1 is deployed
(stub is sufficient for frontend UI work). Phase 6 requires Phases 4, 5, and the IAM grant.

---

## 18. Key Files (Existing, Must Not Break)

| File                                        | Role                                     |
| ------------------------------------------- | ---------------------------------------- |
| `backend/.../dto/HintResponse.java`         | Output contract — do not change          |
| `backend/.../puzzle/hint/HintStrategy.java` | Interface to reuse                       |
| `backend/.../domain/Board.java`             | `fromGrid()`, `calculateAllCandidates()` |
| `backend/.../puzzle/SudokuService.java`     | Add `coach()` method here                |
| `backend/.../puzzle/SudokuServiceImpl.java` | Orchestration pattern to follow          |
| `docs/specs/hint-engine-specs.md`           | 30 specs — all must remain green         |
