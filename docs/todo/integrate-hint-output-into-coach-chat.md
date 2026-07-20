# Integrate hint output into AI coach chat window

**Summary:** Replace the standalone `HintDialog` popup with hint messages posted directly into the `CoachPanel` chat, so hints and coach conversation live in one unified stream instead of two separate UI surfaces.

**Branch context:** `rc-multiinfra-prep` — unrelated infra work; this todo was captured from a user screenshot/discussion, not implemented here.

## Why deferred

User raised this as a UX improvement idea while looking at the current hint popup + coach panel side by side. Out of scope for the current branch (infra move) and needs its own HLD/LLD pass per this repo's Linked-Intent Development workflow before any code changes.

## Context

**Relevant files:**
- `ui/src/components/HintDialog.jsx` — current standalone hint popup (nudge/focus/reveal stages, "Try Different Hint" / "Show Me" / "Got It" actions, `@spec HE-UI-001..005`)
- `ui/src/components/coach/CoachPanel.jsx` — AI coach chat panel (message history, quick-reply chips `["I'm stuck", "Tell me more", "Why does that work?"]`, free-text input, `@spec SC-UI-002..004, SC-RL-009`)
- `ui/src/components/coach/CoachWidget.jsx` — floating launcher that toggles `CoachPanel`
- `ui/src/hooks/useCoachSession.js` — coach chat state/session hook
- `ui/src/utils/hintDisplay.js` — `formatHintText()` used to render hint stage text
- `ui/src/App.jsx` — wires both `HintDialog` and `CoachPanel`/`CoachWidget` together; entry point for any merge

**Current state:**
Today these are two independent UI elements rendered simultaneously (see screenshot: hint card docked below the board with difficulty chip + explanation + "TRY DIFFERENT HINT"/"SHOW ME" buttons, while a separate floating "Sudoku Coach" chat panel sits bottom-right with quick-reply chips and a free-text input). They don't share a transcript — a hint shown in the dialog has no representation in the coach chat history, and vice versa.

**Key constraints:**
- Hint stage flow (nudge → focus → reveal) and its actions are governed by `docs/specs/hint-engine-specs.md` (`HE-UI-001..005`) and `docs/llds/hint-engine.md`.
- Coach chat UI/session behavior is governed by `docs/specs/sudoku-coach-specs.md` (`SC-UI-002..004`, `SC-RL-009`) and `docs/llds/sudoku-coach.md`, including the "Board-Chat Linkage" section (`docs/llds/sudoku-coach.md:464`) which already describes how the coach panel relates to board state — check whether it already anticipates this merge.
- Any UI change must follow `docs/llds/react-frontend.md` Implementation Standards and the Three-Grid State Model.
- Per `CLAUDE.md`, this requires the full Linked-Intent Development workflow (HLD → LLD → EARS → Tests → Code) since it changes/merges two existing feature surfaces, not a bug fix.

## What to do

1. Read `docs/llds/sudoku-coach.md` §"Board-Chat Linkage" and `docs/llds/hint-engine.md` §"Hint Exhaustion Fallback (UI)" to see if a merge was already anticipated or explicitly ruled out.
2. Draft an LLD update (or new section) proposing: hint stages (nudge/focus/reveal) rendered as coach chat messages instead of a separate `HintDialog`; hint actions ("Try Different Hint", "Show Me") become quick-reply chips alongside the existing coach quick replies; free-text input still routes to the coach AI endpoint as today.
3. Update/add EARS specs in `docs/specs/hint-engine-specs.md` and/or `docs/specs/sudoku-coach-specs.md` for the new behavior; mark superseded `HE-UI-*` items appropriately (mutate, don't just append).
4. Decide state ownership: does `useHintSystem` feed messages into `useCoachSession`'s history, or does a new shared hook own both? Document the decision in the LLD before coding.
5. Implement: remove `HintDialog` render from `App.jsx`, extend `CoachPanel`/`CoachMessage` to render hint-stage content + hint action chips, wire hint advance/dismiss/alternate handlers through the chat's quick-reply mechanism.
6. Update/add tests: `ui/src/hooks/useHintSystem.test.js`, `ui/src/hooks/useCoachSession.test.js`, and any `HintDialog`/`CoachPanel` component tests to reflect the merged flow.

## Acceptance criteria

- [ ] LLD/EARS updated and reviewed before code changes (per Linked-Intent Development)
- [ ] Standalone hint popup (`HintDialog`) is removed or no longer rendered for the hint flow
- [ ] Hint nudge/focus/reveal text appears as messages in the coach chat transcript
- [ ] Hint stage actions (advance/alternate/dismiss) are available as clickable chips in the coach chat, alongside free-text input to the AI coach endpoint
- [ ] Existing coach quick replies (`I'm stuck`, `Tell me more`, `Why does that work?`) still work unchanged
- [ ] All existing hint and coach tests pass or are updated to match new behavior; `scripts/local/local-alltests.sh` run before push

## Related specs / docs

- [`docs/specs/hint-engine-specs.md`](../specs/hint-engine-specs.md) — hint stage/action requirements (`HE-UI-*`)
- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — coach chat requirements (`SC-UI-*`, `SC-RL-*`)
- [`docs/llds/hint-engine.md`](../llds/hint-engine.md) — hint stage flow and UI fallback design
- [`docs/llds/sudoku-coach.md`](../llds/sudoku-coach.md) — coach architecture, incl. Board-Chat Linkage section
