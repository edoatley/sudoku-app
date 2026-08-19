# Arrow: Sudoku Coach

Conversational AI tutoring via Amazon Bedrock; deterministic pre-analysis gates every coaching response.

## Status

**IN_PROGRESS** — 2026-07-16. 74/75 specs implemented. 1 gap remains (`SC-UI-013`, Escape key
close). The Bedrock structured-output + caching-fix work
(`docs/planning/old/bedrock-coach-structured-output-plan.md`) is merged, code-complete and
test-verified, including a `responseType` categorical field (SC-BE-028/029) that replaced a
flaky prose-substring assertion in the coach-quality harness (CQ-AST-001); harness A/B
validation against the coach-quality suite is still outstanding. `SC-BE-030` fixes a reported
bug: the coach relayed 0-indexed hint coordinates verbatim (e.g. "Row 7, Column 3" for a cell
that is Row 8, Column 4 on the 1-indexed board) — `HintTextFormatter` now converts
`nudge`/`focus`/`reveal` text before it reaches the LLM, mirroring the frontend's existing
`hintDisplay.js` conversion. Investigating that report also surfaced a related, previously
untracked inconsistency: the coach never acted on `revealHint` at all (highlight-only, even on
an explicit reveal), unlike the Hint button's reveal stage (auto-fills the digit). `SC-UI-050`
and `SC-UI-051` — already-specified but unimplemented — turned out to describe exactly this
behaviour; implementing them (`useCoachSession.js` now writes `solvedCells`/removes
`eliminatedCandidates` when `revealHint` is true, matching `useHintSystem.js`) closed both gaps.

## References

### HLD
- docs/high-level-design.md — "AI Coach" component row; "Data Flow: Coach Message" section

### LLD
- docs/llds/sudoku-coach.md
- docs/planning/ai-guide.md — original design spike

### EARS
- docs/specs/sudoku-coach-specs.md (81 × [x], 2 × [ ])
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
| Bedrock Integration (BE) | SC-BE-010 to SC-BE-018, SC-BE-021 to SC-BE-030 | 19 | 0 |
| AI Provider Port + Vertex AI (BE, GCP) | SC-GCP-001 to SC-GCP-007 | 6 | 1 |
| Coach Response (BE) | SC-API-010 to SC-API-012 | 3 | 0 |
| Widget Rendering (FE) | SC-UI-001 to SC-UI-004 | 4 | 0 |
| Panel Open/Close (FE) | SC-UI-010 to SC-UI-014 | 4 | 1 (SC-UI-013) |
| Conversation Display (FE) | SC-UI-020 to SC-UI-025 | 6 | 0 |
| Quick Reply Chips (FE) | SC-UI-030 to SC-UI-032 | 3 | 0 |
| Board-Chat Linkage (FE) | SC-UI-040 to SC-UI-042 | 3 | 0 |
| Reveal Hint Handling (FE) | SC-UI-050 to SC-UI-051 | 2 | 0 |
| Conversation Lifecycle (FE) | SC-UI-060 to SC-UI-064 | 5 | 0 |
| Rate Limiting & Cost (BE+FE) | SC-RL-001 to SC-RL-010 | 10 | 0 |

**Summary:** 81 of 83 specs implemented; 0 deferred; 2 gaps (SC-UI-013; SC-GCP-007 — Terraform Bedrock-secret drop + validated prod cutover). VertexCoachClient is built + unit-tested; prod stays on Bedrock until coach-quality validation.

## Open Gaps

- **SC-UI-013** — Escape key closes the coach panel. Not implemented.

SC-BE-011/012/014/025/026/027/028/029 (Bedrock structured output + caching fix, plus the
`responseType` categorical field) are now implemented and unit-tested (`BedrockCoachClientTest`,
352 backend tests passing) — see `docs/planning/old/bedrock-coach-structured-output-plan.md`.
`responseType` also unblocked CQ-AST-001 in the coach-quality harness, replacing a flaky
prose-substring assertion (`wrong-guess-acknowledgment` scenario) with a schema-enforced
categorical check. Outstanding before that plan's work is fully closed: the coach-quality harness
A/B run comparing `api-mode=invoke` vs `api-mode=converse` against real Bedrock traffic (plan
§Phase 3), which validates fallback-rate and cache-hit-rate in practice rather than in unit tests.
