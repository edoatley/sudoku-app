# Arrow: Observability

Cross-cutting structured-logging schema — spans Game Lifecycle, Sudoku Coach, and React
Frontend. Consolidates the log-message catalogue those three components' LLDs previously
each documented independently.

## Status

**OK** — 2026-07-10. Documentation-only consolidation; no code changes. All specs referenced
below were already implemented before this arrow was registered.

## References

### LLD
- docs/llds/observability.md — full log-message catalogue, `pid`/`cid` correlation model,
  transport, robustness, storage, download tooling
- docs/llds/game-lifecycle.md — Puzzle-Play Event Logging (component-specific wiring)
- docs/llds/sudoku-coach.md — Content logging (component-specific wiring)
- docs/llds/react-frontend.md — Puzzle-Play Event Buffer (component-specific wiring)

### EARS
This arrow owns no spec IDs of its own — the underlying specs remain owned by the producing
components' own spec files, unchanged by this consolidation:
- docs/specs/game-lifecycle-specs.md — `GL-BE-040..046`, `GL-API-005`
- docs/specs/sudoku-coach-specs.md — `SC-BE-005..020`
- docs/specs/react-frontend-specs.md — `FE-BE-020..024`

### Policy
- docs/arrows/security-standards.md — Logging Policy (what may be logged, retention,
  threat-model rationale — distinct from this arrow's field-schema concern)

### Code
- backend/src/main/java/com/sudoku/game/PuzzleEventLogger.java
- backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java
- ui/src/hooks/useEventLog.js
- ui/src/hooks/useGameSync.js

### Tools
- scripts/logs/download-puzzle-logs.sh
- scripts/logs/download-coach-logs.sh

## Architecture

**Purpose:** Give a single home for "what fields does log line type X carry, and how do
lines correlate across components" — previously scattered across three component LLDs with
no cross-reference. Each producing component still documents its own wiring (how it calls
into the shared logging mechanics) in its own LLD; only the field-by-field schema and the
`pid`/`cid` correlation model moved to `docs/llds/observability.md`.

**Why now:** The puzzle-play event logging feature (`NUMBER`/`NUMBER_RESULT`/`NUMBER_CLEAR`/
`HINT_*`) landed with its intent deliberately distributed across existing arrows — that was
the right Phase-1 call, since it kept each component's LLD self-contained while the feature
was new and its shape was still settling. Once a third component (React Frontend's event
buffer) also depended on the same schema, and `pid` correlation started spanning game-lifecycle
and sudoku-coach lines, the lack of a single reference became a real cost — this arrow closes
that gap.

**Why not new spec IDs:** EARS IDs are stable identifiers cited by `@spec` code annotations
and tests. Moving them would be a rename with no behavioural benefit; the specs correctly
belong to the components that implement them. This arrow is a *reference* consolidation, not
a *ownership* consolidation.
