# Arrow: Sudoku Coach

Conversational AI tutoring via Amazon Bedrock; deterministic pre-analysis gates every coaching response.

## Status

**IN_PROGRESS** — 2026-07-14. 66/69 specs implemented. 3 gaps remain in frontend reveal-hint handling and Escape key close.

## References

### HLD
- docs/high-level-design.md — "AI Coach" component row; "Data Flow: Coach Message" section

### LLD
- docs/llds/sudoku-coach.md
- docs/planning/ai-guide.md — original design spike

### EARS
- docs/specs/sudoku-coach-specs.md (66 × [x], 3 × [ ])
- docs/specs/hint-engine-specs.md — shared `getHint()` path used for pre-analysis

### Tests
- backend/src/test/java/com/sudoku/coach/CoachResourceTest.java — rate-limiting, toggle, 204 path
- backend/src/test/java/com/sudoku/coach/BoardFormatterTest.java — grid rendering

### Code

**Backend:**
- backend/src/main/java/.../coach/web/CoachResource.java
- backend/src/main/java/.../coach/SudokuCoachService.java
- backend/src/main/java/.../coach/SudokuCoachServiceImpl.java
- backend/src/main/java/.../coach/bedrock/BedrockCoachClient.java
- backend/src/main/java/.../coach/bedrock/BoardFormatter.java
- backend/src/main/java/.../coach/bedrock/CoachRateLimiter.java
- backend/src/main/java/.../coach/bedrock/BedrockClientProducer.java
- backend/src/main/java/.../coach/web/CoachRequest.java
- backend/src/main/java/.../coach/web/CoachResponse.java
- backend/src/main/java/.../coach/web/ChatMessage.java

**Frontend:**
- ui/src/components/coach/CoachWidget.jsx
- ui/src/components/coach/CoachPanel.jsx
- ui/src/components/coach/CoachMessage.jsx
- ui/src/hooks/useCoachSession.js

## Architecture

**Purpose:** Teach players *why* a move works by combining deterministic hint engine output with a Bedrock-powered tutor persona. The coach never invents moves — it only annotates the technique the hint engine identified.

**Key design decisions:**
1. **Pre-analysis before Bedrock** — `SudokuCoachServiceImpl` runs `SudokuService.getHint()` first; the result is injected into the Bedrock prompt. This eliminates hallucinated moves and means the entire flow is covered by existing hint-engine tests.
2. **One Bedrock call per request** — `BedrockCoachClient` makes a single `InvokeModel` call. No streaming, no multi-step chain. Fits within the 8 s Lambda timeout.
3. **Raw `BedrockRuntimeClient`** — LangChain4j was evaluated and rejected: it did not expose `cache_control` at the message level, which is required for prompt caching.
4. **Frontend owns history** — Client sends last ≤6 turns with each request. Stateless backend; no extra DynamoDB read per request.
5. **Desktop-only** — `CoachWidget` renders `null` when `useMediaQuery(theme.breakpoints.down('md'))` is true. Gated at build time by `VITE_AI_COACH=true`.

**Rate limiting and cost protection (in `CoachResource`, evaluated in order):**
1. `aiCoachEnabled == false` → 403
2. `coachTokensUsedThisMonth >= monthlyLimit` → 429
3. `CoachRateLimiter.tryConsume(userId)` (DynamoDB atomic, per-minute window) → 429 + `Retry-After`

## EARS Coverage

| Category | Spec IDs | Implemented | Gaps |
| --- | --- | --- | --- |
| Input Validation (BE) | SC-API-001 to SC-API-004 | 4 | 0 |
| Deterministic Pre-Analysis (BE) | SC-BE-001 to SC-BE-004 | 4 | 0 |
| Coordination and Content Logging (BE) | SC-BE-005 to SC-BE-009, SC-BE-019, SC-BE-020 | 7 | 0 |
| Bedrock Integration (BE) | SC-BE-010 to SC-BE-018, SC-BE-021 to SC-BE-024 | 13 | 0 |
| Coach Response (BE) | SC-API-010 to SC-API-012 | 3 | 0 |
| Widget Rendering (FE) | SC-UI-001 to SC-UI-004 | 4 | 0 |
| Panel Open/Close (FE) | SC-UI-010 to SC-UI-014 | 4 | 1 (SC-UI-013) |
| Conversation Display (FE) | SC-UI-020 to SC-UI-025 | 6 | 0 |
| Quick Reply Chips (FE) | SC-UI-030 to SC-UI-032 | 3 | 0 |
| Board-Chat Linkage (FE) | SC-UI-040 to SC-UI-042 | 3 | 0 |
| Reveal Hint Handling (FE) | SC-UI-050 to SC-UI-051 | 0 | 2 |
| Conversation Lifecycle (FE) | SC-UI-060 to SC-UI-064 | 5 | 0 |
| Rate Limiting & Cost (BE+FE) | SC-RL-001 to SC-RL-010 | 10 | 0 |

**Summary:** 66 of 69 specs implemented; 0 deferred; 3 gaps.

## Open Gaps

- **SC-UI-013** — Escape key closes the coach panel. Not implemented.
- **SC-UI-050** — When `revealHint=false`, suppress `hint.reveal`, `hint.solvedCells`, `hint.eliminatedCandidates`. Not implemented; frontend always exposes full hint object.
- **SC-UI-051** — When `revealHint=true`, make all hint fields available alongside the AI message. Not implemented.
